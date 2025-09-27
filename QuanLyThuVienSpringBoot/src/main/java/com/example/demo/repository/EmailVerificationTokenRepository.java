package com.example.demo.repository;

import com.example.demo.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

  Optional<EmailVerificationToken> findByToken(String token);

  void deleteByUserId(Long userId);

  void deleteByExpiresAtBefore(LocalDateTime time);
}
