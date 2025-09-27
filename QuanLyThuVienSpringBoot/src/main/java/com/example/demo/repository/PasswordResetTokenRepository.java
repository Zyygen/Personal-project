package com.example.demo.repository;

import com.example.demo.model.PasswordResetToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("select t from PasswordResetToken t join fetch t.user where t.token = :token")
    Optional<PasswordResetToken> findByTokenFetchUser(@Param("token") String token);

    Optional<PasswordResetToken> findByToken(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    
    @Transactional
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);  
}
