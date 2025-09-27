package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    public enum AccountStatus { ACTIVE, SUSPENDED, BANNED }
    public enum Occupation { STUDENT, LECTURER, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* Đăng nhập bằng email → đồng bộ username = email, cả 2 đều unique */
    @Column(unique = true, nullable = false)
    private String username;   // dùng cho Spring Security

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    /** ROLE_USER | ROLE_ADMIN */
    @Column(nullable = false, length = 20)
    private String role = "ROLE_USER";

    /* Thông tin hồ sơ */
    @Column(name = "full_name")
    private String fullName;

    private String phone;

    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupation", nullable = false, length = 20)
    private Occupation occupation = Occupation.OTHER;

    /**
     * Định danh duy nhất theo yêu cầu:
     *  - STUDENT: studentCode (mã SV) bắt buộc
     *  - LECTURER/OTHER: cccd bắt buộc
     */
    @Column(name = "student_code", unique = true)
    private String studentCode;

    @Column(name = "cccd", unique = true)
    private String cccd;

    /* Trạng thái tài khoản + thành viên */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Thời hạn thành viên (nếu null hoặc đã hết hạn → user thường) */
    @Column(name = "member_until")
    private LocalDateTime memberUntil;

    /* Xác thực email */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Column(name = "verification_sent_at")
    private LocalDateTime verificationSentAt;

    /* Quên mật khẩu */
    @Column(name = "password_reset_token", length = 64)
    private String passwordResetToken;

    @Column(name = "password_reset_expiry")
    private LocalDateTime passwordResetExpiry;

    /* ===== Helpers ===== */
    @Transient
    public boolean isMember() {
        return memberUntil != null && LocalDateTime.now().isBefore(memberUntil);
    }

    /* ===== Getters / Setters ===== */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }

    public Occupation getOccupation() { return occupation; }
    public void setOccupation(Occupation occupation) { this.occupation = occupation; }

    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }

    public String getCccd() { return cccd; }
    public void setCccd(String cccd) { this.cccd = cccd; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public LocalDateTime getMemberUntil() { return memberUntil; }
    public void setMemberUntil(LocalDateTime memberUntil) { this.memberUntil = memberUntil; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public LocalDateTime getVerificationSentAt() { return verificationSentAt; }
    public void setVerificationSentAt(LocalDateTime verificationSentAt) { this.verificationSentAt = verificationSentAt; }

    public String getPasswordResetToken() { return passwordResetToken; }
    public void setPasswordResetToken(String passwordResetToken) { this.passwordResetToken = passwordResetToken; }

    public LocalDateTime getPasswordResetExpiry() { return passwordResetExpiry; }
    public void setPasswordResetExpiry(LocalDateTime passwordResetExpiry) { this.passwordResetExpiry = passwordResetExpiry; }
}
