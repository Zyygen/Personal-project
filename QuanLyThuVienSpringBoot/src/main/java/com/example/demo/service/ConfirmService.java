package com.example.demo.service;

import com.example.demo.model.Borrow;
import com.example.demo.model.BorrowTicket;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.BorrowTicketRepository;
import com.example.demo.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConfirmService {

  private final BorrowTicketService borrowTicketService;
  private final ReturnTicketService returnTicketService;
  private final UserRepository userRepo;

  // ✅ thêm 2 repo để kiểm tra giới hạn & auto-cancel
  private final BorrowRepository borrowRepo;
  private final BorrowTicketRepository borrowTicketRepo;

  public ConfirmService(BorrowTicketService borrowTicketService,
                        ReturnTicketService returnTicketService,
                        UserRepository userRepo,
                        BorrowRepository borrowRepo,
                        BorrowTicketRepository borrowTicketRepo) {
    this.borrowTicketService = borrowTicketService;
    this.returnTicketService = returnTicketService;
    this.userRepo = userRepo;
    this.borrowRepo = borrowRepo;
    this.borrowTicketRepo = borrowTicketRepo;
  }

  /** Kết quả trả về cho UI */
  public record ConfirmResult(Long borrowId, String bookTitle,
                              LocalDateTime dueDate, BigDecimal fineAmount) {}

  /* ===================== XÁC NHẬN CHO MƯỢN ===================== */
  @Transactional
  public ConfirmResult confirmLendByCode(String raw) {
    String token = normalizeCode(raw);
    if (token.isBlank()) {
      throw new IllegalArgumentException("Mã vé/QR rỗng.");
    }

    User admin = currentUserOrThrow();

    // Nghiệp vụ xác nhận & ràng buộc “không vượt giới hạn” đã nằm trong borrowTicketService
    Borrow b = borrowTicketService.confirmByAdmin(token, admin);

    // === Auto-cancel vé PENDING còn lại nếu đã đạt giới hạn ===
    Long userId = safeUserId(b);         // lấy userId từ Borrow (an toàn với Lazy)
    if (userId != null) {
      int limit = resolveBorrowLimit(userId); // ví dụ: thành viên 5, thường 2
      int usedNow = getCurrentUsed(userId);   // tổng đang mượn hiện tại
      if (usedNow >= limit) {
        autoCancelAllPendingOfUser(userId, limit);
      }
    }

    String title = safeBookTitle(b);
    LocalDateTime due = getDueDate(b);
    return new ConfirmResult(b.getId(), title, due, null);
  }

  /* ===================== XÁC NHẬN NHẬN LẠI (TRẢ) ===================== */
  public ConfirmResult confirmReturnByCode(String raw) {
    String token = normalizeCode(raw);
    if (token.isBlank()) {
      throw new IllegalArgumentException("Mã vé/QR rỗng.");
    }

    User admin = currentUserOrThrow();

    // Nghiệp vụ trả nằm trong ReturnTicketService
    Borrow b = returnTicketService.confirmByAdmin(token, admin);

    String title = safeBookTitle(b);
    LocalDateTime due = getDueDate(b);
    BigDecimal fine = getFineAmount(b);
    return new ConfirmResult(b.getId(), title, due, fine);
  }

  /* ===================== Helpers ===================== */

  private User currentUserOrThrow() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      throw new IllegalStateException("Không xác định được ADMIN hiện tại.");
    }
    User u = userRepo.findByUsername(auth.getName());
    if (u == null) {
      u = userRepo.findByEmail(auth.getName()).orElse(null);
    }
    if (u == null) {
      throw new IllegalStateException("Không xác định được ADMIN hiện tại.");
    }
    return u;
  }

  /** Lấy userId từ Borrow một cách an toàn, tránh LazyInitializationException */
  private static Long safeUserId(Borrow b) {
    try {
      Object user = b.getClass().getMethod("getUser").invoke(b);
      if (user == null) return null;
      Object id = user.getClass().getMethod("getId").invoke(user);
      return (id instanceof Long) ? (Long) id : null;
    } catch (Exception ignore) {
      return null;
    }
  }

  private static String safeBookTitle(Borrow b) {
    try {
      Object book = b.getClass().getMethod("getBook").invoke(b);
      if (book != null) {
        Object t = book.getClass().getMethod("getTitle").invoke(book);
        return t == null ? "" : String.valueOf(t);
      }
    } catch (Exception ignore) {}
    return "";
  }

  private static LocalDateTime getDueDate(Borrow b) {
    try {
      return (LocalDateTime) b.getClass().getMethod("getDueDate").invoke(b);
    } catch (Exception e) {
      try {
        return (LocalDateTime) b.getClass().getMethod("getDueAt").invoke(b);
      } catch (Exception ignore) {
        return null;
      }
    }
  }

  private static BigDecimal getFineAmount(Borrow b) {
    try {
      Object v = b.getClass().getMethod("getFineAmount").invoke(b);
      return (v instanceof BigDecimal) ? (BigDecimal) v : null;
    } catch (Exception ignore) {
      return null;
    }
  }

  /**
   * Suy ra giới hạn mượn của user. Ví dụ: thành viên = 5, thường = 2.
   * Bạn có thể thay đổi tùy theo domain (vd. theo role, theo cấp bậc, v.v.)
   */
  private int resolveBorrowLimit(Long userId) {
    User u = userRepo.findById(userId).orElse(null);
    if (u != null) {
      try {
        // nếu User có isMember()
        Boolean isMember = (Boolean) u.getClass().getMethod("isMember").invoke(u);
        if (Boolean.TRUE.equals(isMember)) return 5;
      } catch (Exception ignore) {}
    }
    return 2;
  }

  /**
   * Tổng đang mượn hiện tại.
   * - Nếu Borrow có field amount: dùng SUM(amount).
   * - Nếu không: fallback dùng COUNT(record).
   */
  private int getCurrentUsed(Long userId) {
    try {
      Integer sum = borrowRepo.sumOpenAmountByUser(userId); // cần method trong BorrowRepository
      if (sum != null && sum > 0) return sum;
    } catch (Exception ignore) {}
    Long cnt = 0L;
    try {
      cnt = borrowRepo.countOpenByUser(userId);
    } catch (Exception ignore) {}
    return (cnt == null) ? 0 : cnt.intValue();
  }

  /**
   * Tự động hủy TẤT CẢ vé mượn đang PENDING của user (sau khi đã đạt limit).
   * Nếu entity có các trường cancelledAt/cancelledBy/cancelledReason thì sẽ set; nếu không có thì vẫn chỉ set status.
   */
  private void autoCancelAllPendingOfUser(Long userId, int limit) {
    List<BorrowTicket> pendings =
        borrowTicketRepo.findByUserIdAndStatusOrderByRequestedAtAsc(
            userId, BorrowTicket.TicketStatus.PENDING);

    if (pendings == null || pendings.isEmpty()) return;

    for (BorrowTicket t : pendings) {
      t.setStatus(BorrowTicket.TicketStatus.CANCELLED);
      try { t.getClass().getMethod("setCancelledAt", LocalDateTime.class).invoke(t, LocalDateTime.now()); } catch (Exception ignore) {}
      try { t.getClass().getMethod("setCancelledBy", String.class).invoke(t, "system"); } catch (Exception ignore) {}
      try { t.getClass().getMethod("setCancelledReason", String.class).invoke(t,
              "Tự hủy do tài khoản đã đạt giới hạn mượn (" + limit + ")."); } catch (Exception ignore) {}
    }
    borrowTicketRepo.saveAll(pendings);
  }

  /**
   * Lấy token từ QR/URL (?token=..., ?code=..., …) hoặc chuỗi thuần.
   */
  private static String normalizeCode(String raw) {
    if (raw == null) return "";
    String s = raw.trim();
    try {
      if (s.startsWith("http://") || s.startsWith("https://")) {
        URI uri = URI.create(s);
        String q = uri.getRawQuery();
        if (q != null) {
          java.util.Map<String, String> params = new java.util.HashMap<>();
          for (String kv : q.split("&")) {
            String[] p = kv.split("=", 2);
            String k = URLDecoder.decode(p[0], StandardCharsets.UTF_8);
            String v = p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "";
            params.put(k, v);
          }
          for (String key : new String[]{"token", "code", "qr", "id", "uuid", "hash", "key"}) {
            if (params.containsKey(key) && !params.get(key).isBlank()) {
              return params.get(key).trim();
            }
          }
        }
        String path = uri.getPath();
        if (path != null && !path.isBlank()) {
          String[] seg = path.split("/");
          s = seg[seg.length - 1];
        }
      }
    } catch (Exception ignore) {}

    for (String sep : new String[]{"token=", "code=", "qr=", "id=", "uuid=", "hash=", "key=", "token:", "code:"}) {
      int i = s.indexOf(sep);
      if (i >= 0) return s.substring(i + sep.length()).trim();
    }
    return s;
  }
}
