package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "password_reset_token",
    indexes = {
        @Index(name = "idx_prt_token", columnList = "token", unique = true),
        @Index(name = "idx_prt_user_purpose", columnList = "user_id, purpose")
    }
)
public class PasswordResetToken {

    /** Mục đích của token: RESET (quên mật khẩu) | CHANGE (đổi mật khẩu khi đang đăng nhập) */
    public enum Purpose { RESET, CHANGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Purpose purpose;

    /** Với purpose=CHANGE: lưu sẵn BCrypt hash của mật khẩu mới (không bao giờ lưu plaintext) */
    @Column(name = "new_password_hash", length = 255)
    private String newPasswordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** null = chưa dùng */
    private LocalDateTime usedAt;

    /* ====== Lifecycle ====== */
    /** Phòng trường hợp quên set purpose, mặc định RESET để không vi phạm NOT NULL */
    @PrePersist
    void prePersist() {
        if (purpose == null) purpose = Purpose.RESET;
    }
    

    /* ====== Helpers ====== */
    public boolean isUsed()    { return usedAt != null; }
    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }

    /* ====== Getters/Setters ====== */
    public Long getId() { return id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }

    public String getNewPasswordHash() { return newPasswordHash; }
    public void setNewPasswordHash(String newPasswordHash) { this.newPasswordHash = newPasswordHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    /* ====== Convenient factories (tuỳ dùng) ====== */
    public static PasswordResetToken forReset(User user, String token, LocalDateTime created, LocalDateTime expires) {
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(user);
        t.setToken(token);
        t.setPurpose(Purpose.RESET);
        t.setCreatedAt(created);
        t.setExpiresAt(expires);
        return t;
    }

    public static PasswordResetToken forChange(User user, String token, String newPwdHash,
                                               LocalDateTime created, LocalDateTime expires) {
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(user);
        t.setToken(token);
        t.setPurpose(Purpose.CHANGE);
        t.setNewPasswordHash(newPwdHash);
        t.setCreatedAt(created);
        t.setExpiresAt(expires);
        return t;
    }
}
