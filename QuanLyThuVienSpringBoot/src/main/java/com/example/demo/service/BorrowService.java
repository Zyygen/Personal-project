package com.example.demo.service;

import com.example.demo.model.Book;
import com.example.demo.model.Borrow;
import com.example.demo.model.Reservation;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class BorrowService {

    /** Số ngày “miễn phạt” sau hạn (có thể = 0) */
    @Value("${library.freeDays:0}")
    private int freeDays;

    /** Mức phạt mỗi ngày (VND/ngày) */
    @Value("${library.finePerDay:5000}")
    private int finePerDay;

    /** Chỉ cho phép gia hạn khi còn ≤ threshold ngày đến hạn */
    @Value("${library.extend.thresholdDays:2}")
    private int extendThresholdDays;

    /** Tối đa số ngày được gia hạn cho thành viên */
    @Value("${library.extend.maxDays.member:7}")
    private int memberExtendMax;

    /** Tối đa số ngày được gia hạn cho tài khoản thường */
    @Value("${library.extend.maxDays.normal:3}")
    private int normalExtendMax;

    private final BorrowRepository borrowRepo;
    private final ReservationRepository reservationRepo;

    public BorrowService(BorrowRepository borrowRepo,
                         ReservationRepository reservationRepo) {
        this.borrowRepo = borrowRepo;
        this.reservationRepo = reservationRepo;
    }

    // =========================================================
    // ===============  TẠO/GỘP BORROW THEO DUE DATE  ==========
    // =========================================================

    /**
     * Tạo Borrow mới hoặc gộp vào Borrow đang mở CÙNG hạn trả (dueDate).
     * - Khác dueDate ⇒ tạo bản ghi mới (để UI hiển thị thành dòng riêng).
     * - Cùng dueDate ⇒ cộng dồn amount.
     */
    @Transactional
    public Borrow createOrMergeByDueDate(User user, Book book, int amount, int days) {
        if (user == null || book == null) throw new IllegalArgumentException("User/Book is null");
        if (amount < 1) amount = 1;
        if (days   < 1) days   = 1;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = now.plusDays(days);

        var opt = borrowRepo.findFirstByUserIdAndBookIdAndReturnDateIsNullAndDueDate(
                user.getId(), book.getId(), due
        );

        Borrow b;
        if (opt.isPresent()) {
            b = opt.get();
            b.setAmount(b.getAmount() + amount); // gộp khi cùng hạn trả
        } else {
            b = new Borrow();
            b.setUser(user);
            b.setBook(book);
            b.setAmount(amount);
            b.setBorrowDate(now);
            b.setDueDate(due);
            if (b.getFineAmount() == null)    b.setFineAmount(BigDecimal.ZERO);
            if (b.getFinePaidTotal() == null) b.setFinePaidTotal(BigDecimal.ZERO);
            if (b.getFineStatus() == null)    b.setFineStatus("PAID");
        }

        // Cập nhật trạng thái phạt theo chính sách hiện tại
        calculateOverdue(b);

        return borrowRepo.save(b);
    }

    // =========================================================
    // ===================  TÍNH PHÍ/PHẠT  =====================
    // =========================================================

    /**
     * Tính số ngày quá hạn & tiền phạt.
     *
     * Quy tắc mới:
     * - Dựa trên LocalDateTime (giờ/phút/giây), không quy tròn theo lịch.
     * - Số ngày quá hạn = floor( max(0, (end - due) - freeDays*24h) / 24h ).
     * - Phí phạt = overdueDays * finePerDay * amount.
     * - Cập nhật fineStatus: UNPAID / PENDING / PAID theo tổng đã trả.
     */
    public void calculateOverdue(Borrow borrow) {
        if (borrow == null) return;

        if (borrow.getDueDate() == null) {
            borrow.setOverdueDays(0);
            borrow.setFineAmount(BigDecimal.ZERO);
            borrow.setFineStatus("PAID");
            return;
        }

        // Mốc tính: nếu đã trả thì lấy thời điểm trả, chưa trả thì lấy "bây giờ"
        LocalDateTime dueDt = borrow.getDueDate();
        LocalDateTime endDt = (borrow.getReturnDate() != null)
                ? borrow.getReturnDate()
                : LocalDateTime.now();

        // Thời lượng trễ theo GIỜ (âm thì coi như 0)
        long totalHoursLate = Math.max(0L, Duration.between(dueDt, endDt).toHours());

        // Trừ đi số giờ miễn phạt, sau đó lấy phần nguyên theo 24h
        long effectiveHours = Math.max(0L, totalHoursLate - (long) freeDays * 24L);
        int overdue = (int) (effectiveHours / 24L);
        borrow.setOverdueDays(overdue);

        // Nhân theo số lượng mượn
        int qty = (borrow.getAmount() != null && borrow.getAmount() > 0) ? borrow.getAmount() : 1;

        BigDecimal fine = (overdue > 0)
                ? BigDecimal.valueOf((long) overdue * (long) finePerDay * (long) qty)
                : BigDecimal.ZERO;
        borrow.setFineAmount(fine);

        BigDecimal paid = (borrow.getFinePaidTotal() == null)
                ? BigDecimal.ZERO
                : borrow.getFinePaidTotal();

        String newStatus;
        if (fine.signum() == 0) {
            newStatus = "PAID";
        } else if (paid.compareTo(fine) >= 0) {
            newStatus = "PAID";
        } else if (paid.signum() > 0) {
            newStatus = "PENDING";
        } else {
            newStatus = "UNPAID";
        }
        borrow.setFineStatus(newStatus);
    }

    // =========================================================
    // ======================  GIA HẠN  ========================
    // =========================================================

    /** Còn bao nhiêu ngày đến hạn (âm nếu đã quá hạn) – tính theo lịch (ngày) để đơn giản cho UI. */
    public int daysLeft(Borrow b) {
        if (b.getDueDate() == null) return Integer.MAX_VALUE;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), b.getDueDate().toLocalDate());
    }

    /**
     * Gắn cờ cho View: b.canExtend, b.maxExtendDays, b.defaultExtendDays.
     * Nếu entity Borrow KHÔNG có các field này, hàm sẽ cố gắng set qua reflection.
     */
    public void annotateExtendability(Borrow b, boolean isMember) {
        if (b == null || b.getBook() == null) return;

        int left = daysLeft(b);

        // Có ai đặt chỗ?
        boolean hasActiveReservation =
                reservationRepo.existsByBook_IdAndStatusIn(
                        b.getBook().getId(),
                        java.util.List.of(Reservation.Status.PENDING)
                );

        // Còn hàng?
        Integer qty = null;
        try { qty = b.getBook().getQuantity(); } catch (Exception ignore) {}
        boolean inStock = (qty == null) || (qty > 0); // nếu model không có quantity thì coi như còn

        // ❗ Chỉ chặn khi (có người đặt chỗ) VÀ (hết hàng); các trường hợp còn lại đều OK
        boolean blockByStockAndHold = hasActiveReservation && !inStock;

        boolean can = (b.getReturnDate() == null)
                && (left <= extendThresholdDays)
                && !blockByStockAndHold;

        int max = isMember ? memberExtendMax : normalExtendMax;
        int def = Math.min(3, max);

        trySetTransient(b, "setCanExtend", boolean.class, can);
        trySetTransient(b, "setMaxExtendDays", Integer.class, max);
        trySetTransient(b, "setDefaultExtendDays", Integer.class, def);
    }

    @Transactional
    public Borrow extendBorrow(Long borrowId, int addDays, Long currentUserId, boolean isMember) {
        Borrow b = borrowRepo.findById(borrowId).orElseThrow();

        if (!b.getUser().getId().equals(currentUserId)) {
            throw new SecurityException("Bạn không có quyền gia hạn phiếu này.");
        }
        if (b.getReturnDate() != null) {
            throw new IllegalStateException("Phiếu mượn đã trả, không thể gia hạn.");
        }

        int left = daysLeft(b);
        if (left > extendThresholdDays) {
            throw new IllegalStateException("Chỉ được gia hạn khi còn " + extendThresholdDays + " ngày hoặc ít hơn.");
        }

        int max = isMember ? memberExtendMax : normalExtendMax;
        if (addDays < 1 || addDays > max) {
            throw new IllegalArgumentException("Số ngày gia hạn phải trong khoảng 1.." + max + ".");
        }

        boolean hasActiveReservation =
                reservationRepo.existsByBook_IdAndStatusIn(
                        b.getBook().getId(),
                        java.util.List.of(Reservation.Status.PENDING)
                );

        Integer qty = null;
        try { qty = b.getBook().getQuantity(); } catch (Exception ignore) {}
        boolean inStock = (qty == null) || (qty > 0);

        // ❗ Chỉ chặn trong TH đồng thời có người đặt chỗ và hết hàng
        if (hasActiveReservation && !inStock) {
            throw new IllegalStateException("Không thể gia hạn: đang có người đặt chỗ và kho đã hết sách.");
        }

        // Tiến hành gia hạn
        b.setDueDate(b.getDueDate().plusDays(addDays));

        // Cập nhật lại phí/phạt theo quy tắc 24h
        calculateOverdue(b);

        return borrowRepo.save(b);
    }

    // =========================================================
    // =====================  Helpers  =========================
    // =========================================================

    /** Set các thuộc tính "transient" cho View (tự thử primitive lẫn wrapper). */
    private void trySetTransient(Borrow b, String setter, Class<?> type, Object value) {
        // 1) thử đúng chữ ký được truyền vào
        try {
            Method m = b.getClass().getMethod(setter, type);
            m.invoke(b, value);
            return;
        } catch (Exception ignored) {}

        // 2) thử đảo primitive <-> wrapper để tăng khả năng khớp
        try {
            if (type == boolean.class) {
                Method m = b.getClass().getMethod(setter, Boolean.class);
                m.invoke(b, (Boolean) value);
                return;
            }
            if (type == Boolean.class) {
                Method m = b.getClass().getMethod(setter, boolean.class);
                m.invoke(b, ((Boolean) value).booleanValue());
                return;
            }
            if (type == int.class) {
                Method m = b.getClass().getMethod(setter, Integer.class);
                m.invoke(b, Integer.valueOf((Integer) value));
                return;
            }
            if (type == Integer.class) {
                Method m = b.getClass().getMethod(setter, int.class);
                m.invoke(b, ((Integer) value).intValue());
                return;
            }
        } catch (Exception ignored) {
            // nếu entity không có các field này thì bỏ qua là được
        }
    }
}
