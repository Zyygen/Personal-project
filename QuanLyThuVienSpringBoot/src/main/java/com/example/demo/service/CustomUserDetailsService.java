package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepo;
  public CustomUserDetailsService(UserRepository repo){ this.userRepo = repo; }

  @Override
  public UserDetails loadUserByUsername(String input) throws UsernameNotFoundException {
      // Ưu tiên email trước (UI của bạn hiển thị Email)
      User u = userRepo.findByEmail(input).orElse(null);
      if (u == null) {
          u = userRepo.findByUsername(input);
      }
      if (u == null) {
          throw new UsernameNotFoundException("Không tìm thấy người dùng");
      }

      boolean emailOk   = u.isEmailVerified() || "ROLE_ADMIN".equals(u.getRole());
      boolean locked    = u.getStatus() == User.AccountStatus.BANNED;
      boolean suspended = u.getStatus() == User.AccountStatus.SUSPENDED;

      String principal = (u.getEmail() != null && !u.getEmail().isBlank())
              ? u.getEmail()
              : u.getUsername(); // tránh null

      // Map authority an toàn
      List<SimpleGrantedAuthority> auths = List.of(new SimpleGrantedAuthority(u.getRole()));

      return org.springframework.security.core.userdetails.User
          .withUsername(principal)
          .password(u.getPassword())          // PHẢI là BCrypt
          .authorities(auths)
          .accountExpired(false)
          .accountLocked(locked)
          .credentialsExpired(false)
          .disabled(!emailOk || suspended)
          .build();
  }
}
