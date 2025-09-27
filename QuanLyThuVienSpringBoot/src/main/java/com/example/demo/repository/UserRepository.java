package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);

    Optional<User> findByVerificationToken(String token);
    Optional<User> findByPasswordResetToken(String token);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByCccd(String cccd);

    boolean existsByStudentCode(String studentCode);

    default boolean existsByStudentId(String studentId) {
        return existsByStudentCode(studentId);
    }

    List<User> findByRole(String role);

}
