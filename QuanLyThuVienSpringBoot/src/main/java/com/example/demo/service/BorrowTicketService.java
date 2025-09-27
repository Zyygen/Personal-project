package com.example.demo.service;

import com.example.demo.model.Book;
import com.example.demo.model.Borrow;
import com.example.demo.model.BorrowTicket;
import com.example.demo.model.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.BorrowTicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BorrowTicketService {

  private final BorrowTicketRepository ticketRepo;
  private final BorrowRepository borrowRepo;
  private final BookRepository bookRepo;
  private final QrService qrService;
  private final BorrowService borrowService;

  @Value("${APP_BASE_URL:}")
  private String baseUrl;

  public BorrowTicketService(BorrowTicketRepository t,
                             BorrowRepository b,
                             BookRepository br,
                             QrService qr,
                             BorrowService borrowService) {
    this.ticketRepo = t;
    this.borrowRepo = b;
    this.bookRepo = br;
    this.qrService = qr;
    this.borrowService = borrowService;
  }

  public record TicketView(BorrowTicket ticket, String qrDataUri) {}

  /* ------------------------ Helpers ------------------------ */

  /** Giới hạn tổng số CUỐN theo loại tài khoản (tùy hệ thống của bạn có thể đọc từ MembershipService). */
  private int maxBooksOf(User user) {
    return user.isMember() ? 5 : 2;
  }

  /** Tổng số CUỐN đang mượn (chưa trả) — đếm từ bảng BORROW. */
  private int activeBorrowQty(Long userId) {
    return borrowRepo.sumActiveAmount(userId);
  }

  /** Chốt chặn khi tạo vé: CHỈ dựa vào số đang mượn trong bảng borrow. */
  private void enforceLimitForCreate(User user, int addingAmount) {
    int active = activeBorrowQty(user.getId());
    int limit  = maxBooksOf(user);
    int allowed = limit - active;
    if (allowed <= 0 || addingAmount > allowed) {
      throw new IllegalStateException(
          "Bạn đang mượn " + active + " cuốn. Giới hạn tối đa là " + limit +
          " cuốn.");
    }
  }

  /** Chốt chặn khi xác nhận: CHỈ dựa vào số đang mượn trong bảng borrow. */
  private void enforceLimitForConfirm(User user, int addingAmount) {
    int active = activeBorrowQty(user.getId());
    int limit  = maxBooksOf(user);
    int allowed = limit - active;
    if (allowed <= 0 || addingAmount > allowed) {
      throw new IllegalStateException(
          "Hiện đang mượn " + active + "/" + limit +
          " cuốn. Không thể xác nhận thêm " + addingAmount + " cuốn.");
    }
  }

  /* ------------------------ Queries ------------------------ */

  /** DÙNG CHO /qr-history: trả về tất cả vé mượn PENDING của user (không ảnh hưởng limit). */
  @Transactional(readOnly = true)
  public List<BorrowTicket> findPendingByUser(User user) {
    return ticketRepo.findByUserIdAndStatus(user.getId(), BorrowTicket.TicketStatus.PENDING);
  }

  /* ------------------------ Create / Cancel / Confirm ------------------------ */

  /** Tạo vé mượn + QR (người dùng) — kiểm tra limit chỉ theo bảng borrow. */
  @Transactional
  public TicketView createTicket(User user, Long bookId, int amount, int days) {
    // Khóa vì quá hạn?
    if (borrowRepo.existsByUserIdAndReturnDateIsNullAndDueDateBefore(user.getId(), LocalDateTime.now())) {
      throw new IllegalStateException(
          "Tài khoản đang bị khóa do có sách quá hạn. Hãy tạo mã trả và hoàn tất trả trước khi mượn mới.");
    }

    if (amount < 1) amount = 1;
    enforceLimitForCreate(user, amount); // ✅ chỉ dựa vào BORROW

    // Giới hạn ngày theo loại tài khoản
    int maxDays = user.isMember() ? 14 : 7;
    if (days < 1 || days > maxDays) {
      throw new IllegalArgumentException("Số ngày mượn vượt quá giới hạn (" + maxDays + ").");
    }

    Book book = bookRepo.findById(bookId).orElseThrow();
    if (book.getQuantity() < amount) {
      throw new IllegalStateException("Sách đã hết — hãy dùng chức năng đặt chỗ.");
    }

    LocalDateTime now = LocalDateTime.now();
    BorrowTicket t = new BorrowTicket();
    t.setUser(user);
    t.setBook(book);
    t.setAmount(amount);
    t.setDays(days);
    t.setRequestedAt(now);
    t.setExpiresAt(now.plusDays(1));
    t.setStatus(BorrowTicket.TicketStatus.PENDING);
    t.setToken(java.util.UUID.randomUUID().toString().replace("-", ""));
    ticketRepo.save(t);

    String scanUrl = normalizeBaseUrl(baseUrl) + "/admin/ticket/scan?token=" + t.getToken();
    return new TicketView(t, qrService.toDataUriPng(scanUrl, 280));
  }

  /** Người dùng tự hủy vé PENDING. */
  @Transactional
  public void cancelByOwner(User user, Long ticketId) {
    BorrowTicket t = ticketRepo.findById(ticketId).orElseThrow();
    if (!t.getUser().getId().equals(user.getId())) {
      throw new SecurityException("Không có quyền với vé này.");
    }
    if (t.getStatus() != BorrowTicket.TicketStatus.PENDING) {
      throw new IllegalStateException("Vé không còn hiệu lực (đã xác nhận/hết hạn/đã huỷ).");
    }
    t.setStatus(BorrowTicket.TicketStatus.CANCELLED);
  }

  /**
   * Admin xác nhận vé khi quét QR:
   * - Khóa vé chống double-scan
   * - Check hết hạn/tồn kho
   * - Check limit CHỈ theo bảng borrow
   * - Tạo/gộp Borrow theo dueDate
   * - Trừ kho, set CONFIRMED
   */
  @Transactional
  public Borrow confirmByAdmin(String token, User admin) {
      // Lấy ticket dưới khóa ghi để tránh 2 lần quét trùng
      var t = ticketRepo.findByToken(token)
              .orElseThrow(() -> new IllegalArgumentException("Vé không tồn tại"));

      if (t.getStatus() != BorrowTicket.TicketStatus.PENDING) {
          throw new IllegalStateException("Vé đã được xử lý (" + t.getStatus() + ")");
      }
      if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
          t.setStatus(BorrowTicket.TicketStatus.EXPIRED);
          ticketRepo.saveAndFlush(t);
          throw new IllegalStateException("Vé đã hết hạn.");
      }

      var book = t.getBook();
      if (book.getQuantity() < t.getAmount()) {
          throw new IllegalStateException("Sách đã hết.");
      }

      var now = LocalDateTime.now();

      // Tạo borrow
      var b = new Borrow();
      b.setUser(t.getUser());
      b.setBook(book);
      b.setBorrowDate(now);
      b.setDueDate(now.plusDays(t.getDays()));
      b.setAmount(t.getAmount());
      b.setFineStatus("UNPAID");
      b.setFinePaidTotal(java.math.BigDecimal.ZERO);
      borrowRepo.save(b);

      // Trừ kho
      book.setQuantity(book.getQuantity() - t.getAmount());
      bookRepo.save(book);

      // Chuyển trạng thái vé
      t.setStatus(BorrowTicket.TicketStatus.CONFIRMED);
      t.setConfirmedBy(admin);
      t.setConfirmedAt(now);
      ticketRepo.saveAndFlush(t); // ❗ flush ngay để các request khác thấy trạng thái mới

      return b;
  }

  /** Cron: tự hết hạn vé PENDING quá hạn (mỗi 10 phút) */
  @Scheduled(cron = "0 */10 * * * *")
  @Transactional
  public void expireJob() {
    ticketRepo.expireOldTickets();
  }

  private String normalizeBaseUrl(String url) {
    if (url == null || url.isBlank()) return "";
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
