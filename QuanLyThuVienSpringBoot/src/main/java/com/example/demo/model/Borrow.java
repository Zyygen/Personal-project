package com.example.demo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "borrow")
public class Borrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "borrow_date")
    private LocalDateTime borrowDate;

    @Column(name = "return_date")
    private LocalDateTime returnDate;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(nullable = false)
    private Integer amount = 1;

    @Column(name = "overdue_days")
    private Integer overdueDays; // lưu DB (nếu bạn snapshot)

    @Column(name = "fine_amount", nullable = false)
    private BigDecimal fineAmount = BigDecimal.ZERO; // lưu DB (nếu bạn snapshot)

    @Column(name = "fine_status", nullable = false, length = 16)
    private String fineStatus = "UNPAID"; // UNPAID | PENDING | PAID | WAIVED

    @Column(name = "fine_paid_total", nullable = false)
    private BigDecimal finePaidTotal = BigDecimal.ZERO;

    @Column(name = "fine_paid_at")
    private LocalDateTime finePaidAt;

    @Transient
    private boolean canExtend;

    @Transient
    private Integer maxExtendDays;

    @Transient
    private Integer defaultExtendDays;

    // ====== GETTERS / SETTERS ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }

    public LocalDateTime getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDateTime borrowDate) { this.borrowDate = borrowDate; }

    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }

    public BigDecimal getFineAmount() { return fineAmount; }
    public void setFineAmount(BigDecimal fineAmount) { this.fineAmount = fineAmount; }

    public String getFineStatus() { return fineStatus; }
    public void setFineStatus(String fineStatus) { this.fineStatus = fineStatus; }

    public BigDecimal getFinePaidTotal() { return finePaidTotal; }
    public void setFinePaidTotal(BigDecimal finePaidTotal) { this.finePaidTotal = finePaidTotal; }

    public LocalDateTime getFinePaidAt() { return finePaidAt; }
    public void setFinePaidAt(LocalDateTime finePaidAt) { this.finePaidAt = finePaidAt; }

    public boolean isCanExtend() { return canExtend; }
    public void setCanExtend(boolean canExtend) { this.canExtend = canExtend; }

    public Integer getMaxExtendDays() { return maxExtendDays; }
    public void setMaxExtendDays(Integer maxExtendDays) { this.maxExtendDays = maxExtendDays; }

    public Integer getDefaultExtendDays() { return defaultExtendDays; }
    public void setDefaultExtendDays(Integer defaultExtendDays) { this.defaultExtendDays = defaultExtendDays; }

    // ====== HELPERS (không map DB) ======
    /** Mức phạt mặc định: 5.000 VND/ngày/1 cuốn */
    public static final long DAILY_FINE_VND = 5_000L;

    /**
     * Số ngày trễ tính theo khối 24h, KHÔNG làm tròn (đủ 24h mới tính 1 ngày).
     * Ví dụ: trễ 47h59m -> 1 ngày; trễ 48h00m -> 2 ngày.
     */
    @Transient
    public int calcOverdueDays24h() {
        if (getDueDate() == null) return 0;

        LocalDateTime end = (getReturnDate() != null) ? getReturnDate() : LocalDateTime.now();
        if (!end.isAfter(getDueDate())) return 0;

        long minutesLate = Duration.between(getDueDate(), end).toMinutes();
        long days = minutesLate / 1440; // 1440 phút = 24h → chia lấy phần nguyên
        return (int) Math.max(days, 0);
    }

    /**
     * Giữ tương thích: từ nay calcOverdueDays() dùng chuẩn 24h.
     */
    @Transient
    public int calcOverdueDays() {
        return calcOverdueDays24h();
    }

    /** Phí phạt = ngày_trễ(24h) × số_cuốn × rate (VND). */
    @Transient
    public BigDecimal calcFineAmount(long dailyRateVnd) {
        int qty = (getAmount() != null ? getAmount() : 1);
        long days = calcOverdueDays24h();
        return BigDecimal.valueOf(days)
                .multiply(BigDecimal.valueOf(qty))
                .multiply(BigDecimal.valueOf(dailyRateVnd));
    }

    /** Bản tương thích tham số int. */
    @Transient
    public BigDecimal calcFineAmount(int dailyRateVnd) {
        return calcFineAmount((long) dailyRateVnd);
    }

    /** Ngày trễ "hiệu lực": ưu tiên giá trị DB nếu có, ngược lại tính 24h. */
    @Transient
    public int getOverdueDaysEffective24h() {
        return (getOverdueDays() != null ? getOverdueDays() : calcOverdueDays24h());
    }

    /** Tiền phạt "hiệu lực": ưu tiên DB (>0), ngược lại tính theo rate truyền vào. */
    @Transient
    public BigDecimal getFineAmountEffective(long dailyRateVnd) {
        if (getFineAmount() != null && getFineAmount().signum() > 0) {
            return getFineAmount();
        }
        return calcFineAmount(dailyRateVnd);
    }
}
