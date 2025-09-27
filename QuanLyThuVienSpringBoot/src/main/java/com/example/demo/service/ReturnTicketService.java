package com.example.demo.service;

import com.example.demo.model.Borrow;
import com.example.demo.model.ReturnTicket;
import com.example.demo.model.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.ReturnTicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ReturnTicketService {

  private final ReturnTicketRepository repo;
  private final BorrowRepository borrowRepo;
  private final BookRepository bookRepo;
  private final QrService qrService;
  private final BorrowService borrowService;

  @Value("${APP_BASE_URL:}")
  private String baseUrl;

  // === Cấu hình luật phạt ===
  @Value("${library.finePerDay:5000}")
  private long finePerDay;

  @Value("${library.freeDays:0}")
  private int freeDays;

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public ReturnTicketService(ReturnTicketRepository repo,
                             BorrowRepository borrowRepo,
                             BookRepository bookRepo,
                             QrService qrService,
                             BorrowService borrowService) {
    this.repo = repo;
    this.borrowRepo = borrowRepo;
    this.bookRepo = bookRepo;
    this.qrService = qrService;
    this.borrowService = borrowService;
  }

  public record TicketView(ReturnTicket ticket, String qrDataUri) {}

  /* ================== Query for UI ================== */

  /** DÙNG CHO /qr-history (tab trả) */
  @Transactional(readOnly = true)
  public List<ReturnTicket> findPendingByUser(User user) {
    return repo.findByBorrow_User_IdAndStatusOrderByRequestedAtDesc(
        user.getId(), ReturnTicket.TicketStatus.PENDING
    );
  }

  @Transactional(readOnly = true)
  public Optional<ReturnTicket> findById(Long id) {
    return repo.findById(id);
  }

  /* ================== Rules ================== */

  /** Tính phí phạt theo cấu hình tại thời điểm now (không dựa dữ liệu cũ). */
  private BigDecimal computeFineNow(Borrow b, LocalDateTime now) {
    if (b == null || b.getDueDate() == null) return ZERO;

    long rawLate = Duration.between(b.getDueDate(), now).toDays(); // có thể âm
    if (rawLate <= 0) return ZERO;

    long lateDays = Math.max(0, rawLate - Math.max(0, freeDays));
    if (lateDays == 0) return ZERO;

    long qty = (b.getAmount() == null ? 1 : b.getAmount());
    long vnd = lateDays * Math.max(0, finePerDay) * Math.max(1, qty);
    return (vnd <= 0) ? ZERO : BigDecimal.valueOf(vnd);
  }

  /** Đọc tổng tiền phạt đã thanh toán (nếu có) qua reflection để không phụ thuộc model. */
  private static BigDecimal safeFinePaidTotal(Borrow b) {
    try {
      Method m = b.getClass().getMethod("getFinePaidTotal");
      Object v = m.invoke(b);
      return (v instanceof BigDecimal bd) ? bd : ZERO;
    } catch (Exception ignore) {
      return ZERO;
    }
  }

  /** Chỉ chặn khi fineNow > 0 và paid < fineNow. */
  private void enforceFinePaidBeforeCreate(Borrow b) {
    BigDecimal fineNow = computeFineNow(b, LocalDateTime.now());
    if (fineNow.compareTo(ZERO) <= 0) return; // không phạt -> cho tạo QR

    BigDecimal paid = safeFinePaidTotal(b);
    if (paid.compareTo(fineNow) < 0) {
      BigDecimal remain = fineNow.subtract(paid);
      String msg = "Phiếu mượn đã quá hạn. Vui lòng thanh toán phí phạt "
          + currencyVn(remain) + " trước khi tạo mã trả.";
      throw new IllegalStateException(msg);
    }
  }

  /** Phòng hờ: admin confirm cũng không cho khi còn nợ phạt > 0. */
  private void enforceFinePaidBeforeConfirm(Borrow b) {
    BigDecimal fineNow = computeFineNow(b, LocalDateTime.now());
    if (fineNow.compareTo(ZERO) <= 0) return;

    BigDecimal paid = safeFinePaidTotal(b);
    if (paid.compareTo(fineNow) < 0) {
      throw new IllegalStateException("Vé quá hạn nhưng phí phạt chưa thanh toán đủ. Vui lòng thu phí trước khi xác nhận.");
    }
  }

  /* ================== Create / Cancel / Confirm ================== */

  /** Tạo vé trả + QR cho một borrow chưa trả (chặn nếu quá hạn mà còn nợ phạt) */
  @Transactional
  public TicketView createTicket(Long borrowId) {
    Borrow borrow = borrowRepo.findById(borrowId).orElseThrow();
    if (borrow.getReturnDate() != null) {
      throw new IllegalStateException("Mục này đã trả.");
    }

    // CHỐT CHẶN: nếu còn nợ phạt tại thời điểm hiện tại -> cấm tạo QR
    enforceFinePaidBeforeCreate(borrow);

    LocalDateTime now = LocalDateTime.now();
    ReturnTicket t = new ReturnTicket();
    t.setBorrow(borrow);
    t.setRequestedAt(now);
    t.setExpiresAt(now.plusDays(1));
    t.setStatus(ReturnTicket.TicketStatus.PENDING);
    t.setToken(java.util.UUID.randomUUID().toString().replace("-", ""));
    repo.save(t);

    String scanUrl = normalizeBaseUrl(baseUrl) + "/admin/return/scan?token=" + t.getToken();
    String qr = qrService.toDataUriPng(scanUrl, 280);
    return new TicketView(t, qr);
  }

  /** User tự hủy vé trả khi còn PENDING */
  @Transactional
  public void cancelByOwner(User user, Long ticketId) {
    ReturnTicket t = repo.findById(ticketId).orElseThrow();
    if (t.getBorrow() == null || t.getBorrow().getUser() == null
        || !t.getBorrow().getUser().getId().equals(user.getId())) {
      throw new SecurityException("Bạn không có quyền hủy vé này.");
    }
    if (t.getStatus() != ReturnTicket.TicketStatus.PENDING) {
      throw new IllegalStateException("Vé không còn hiệu lực để hủy.");
    }
    t.setStatus(ReturnTicket.TicketStatus.CANCELLED);
    repo.save(t);
  }

  /**
   * Admin xác nhận vé trả:
   * - kiểm tra trạng thái/hết hạn
   * - chặn nếu còn nợ phạt > 0
   * - set returnDate, snapshot phạt nếu cần, trả sách về kho
   * - set vé CONFIRMED
   */
  @Transactional
  public Borrow confirmByAdmin(String token, User admin) {
    ReturnTicket t = repo.findByToken(token)
        .orElseThrow(() -> new IllegalStateException("Vé không tồn tại."));
    if (t.getStatus() != ReturnTicket.TicketStatus.PENDING) {
      throw new IllegalStateException("Vé không còn hiệu lực.");
    }

    LocalDateTime now = LocalDateTime.now();
    if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(now)) {
      t.setStatus(ReturnTicket.TicketStatus.EXPIRED);
      repo.save(t);
      throw new IllegalStateException("Vé đã hết hạn.");
    }

    Borrow b = t.getBorrow();
    if (b == null) {
      t.setStatus(ReturnTicket.TicketStatus.CANCELLED);
      repo.save(t);
      throw new IllegalStateException("Phiếu mượn không hợp lệ.");
    }
    if (b.getReturnDate() != null) {
      t.setStatus(ReturnTicket.TicketStatus.CANCELLED);
      repo.save(t);
      throw new IllegalStateException("Mục này đã trả.");
    }

    // CHỐT CHẶN: còn nợ phạt tại thời điểm xác nhận -> không cho xác nhận
    enforceFinePaidBeforeConfirm(b);

    // 1) Đánh dấu trả
    b.setReturnDate(now);

    // 2) Snapshot phạt/quá hạn theo policy nội bộ (giữ nguyên)
    borrowService.calculateOverdue(b);
    borrowRepo.save(b);

    // 3) Trả sách về kho
    var book = b.getBook();
    book.setQuantity(book.getQuantity() + b.getAmount());
    try {
      var setAvailable = book.getClass().getMethod("setAvailable", boolean.class);
      setAvailable.invoke(book, book.getQuantity() > 0);
    } catch (Exception ignored) {}
    bookRepo.save(book);

    // 4) Chốt vé
    t.setStatus(ReturnTicket.TicketStatus.CONFIRMED);
    t.setConfirmedBy(admin);
    t.setConfirmedAt(now);
    repo.save(t);

    return b;
  }

  /** Cron: tự hết hạn PENDING quá hạn (mỗi 10 phút) */
  @Scheduled(cron = "0 */10 * * * *")
  @Transactional
  public void expireJob() {
    repo.expireOldTickets();
  }

  /* ================== Helpers ================== */

  private String normalizeBaseUrl(String url) {
    if (url == null || url.isBlank()) return "";
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String currencyVn(BigDecimal v) {
    if (v == null) return "0 ₫";
    NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    return nf.format(v);
  }
}
