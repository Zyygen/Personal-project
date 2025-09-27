package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations",
       indexes = {
         @Index(name = "ix_resv_book_status", columnList = "book_id,status,createdAt"),
         @Index(name = "ix_resv_user_status", columnList = "user_id,status,createdAt")
       })
public class Reservation {

    public enum Status {
        PENDING,   // xếp hàng đợi, khi sách hết
        READY,     // đã có đúng 1 cuốn -> giữ cho người đầu hàng
        FULFILLED, // đã mượn thành công từ đặt chỗ
        CANCELLED, // người dùng hủy
        EXPIRED    // hết hạn giữ (mặc định 24h) mà không đến mượn
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readyAt;    // thời điểm set READY
    private LocalDateTime expireAt;   // READY đến khi nào (giữ 24h)
    private LocalDateTime fulfilledAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime expiredAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ===== getters/setters =====
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime readyAt) { this.readyAt = readyAt; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(LocalDateTime fulfilledAt) { this.fulfilledAt = fulfilledAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
}
