package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuanLyThuVienSpringBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuanLyThuVienSpringBootApplication.class, args);
    }

    @Bean
    public CommandLineRunner initAdmins(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            createAdminIfMissing(userRepository, encoder, "admin@eaut.edu.vn",  "admin1234");
            createAdminIfMissing(userRepository, encoder, "admin2@eaut.edu.vn", "admin1234");
        };
    }

    private void createAdminIfMissing(UserRepository userRepository,
                                      PasswordEncoder encoder,
                                      String emailAsUsername,
                                      String rawPassword) {
        User existing = userRepository.findByUsername(emailAsUsername);
        if (existing == null) {
            User admin = new User();
            admin.setUsername(emailAsUsername);
            admin.setEmail(emailAsUsername);
            admin.setPassword(encoder.encode(rawPassword));
            admin.setRole("ROLE_ADMIN");

            userRepository.save(admin);
        }
    }
}
