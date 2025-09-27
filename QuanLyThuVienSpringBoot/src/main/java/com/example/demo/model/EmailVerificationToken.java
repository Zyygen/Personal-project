package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_token",
       indexes = {
         @Index(name="idx_evt_token", columnList="token", unique = true),
         @Index(name="idx_evt_user", columnList="user_id"),
         @Index(name="idx_evt_expires", columnList="expires_at")
       })
public class EmailVerificationToken {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, unique = true, length = 64)
  private String token;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public User getUser() { return user; }
  public void setUser(User user) { this.user = user; }

  public String getToken() { return token; }
  public void setToken(String token) { this.token = token; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
