package com.example.demo.service;

import com.example.demo.model.EmailVerificationToken;
import com.example.demo.model.User;
import com.example.demo.repository.EmailVerificationTokenRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Dịch vụ xác minh email dùng bảng EmailVerificationToken.
 */
@Service
public class VerificationService {

    private final UserRepository userRepo;
    private final EmailVerificationTokenRepository tokenRepo;
    private final MailService mailService;

    @Value("${app.verification.expire-hours:48}")
    private long expireHours;

    public VerificationService(UserRepository userRepo,
                               EmailVerificationTokenRepository tokenRepo,
                               MailService mailService) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.mailService = mailService;
    }

    /** Tạo token và gửi email xác minh cho user (dùng khi đăng ký hoặc gửi lại). */
    @Transactional
    public void sendVerificationEmail(User u) {
        // Xoá token cũ nếu có (mỗi lần chỉ giữ 1 token hiệu lực)
        tokenRepo.deleteByUserId(u.getId());

        EmailVerificationToken t = new EmailVerificationToken();
        t.setUser(u);
        t.setToken(UUID.randomUUID().toString());
        t.setCreatedAt(LocalDateTime.now());
        t.setExpiresAt(LocalDateTime.now().plusHours(expireHours));
        tokenRepo.saveAndFlush(t); // ✅ flush trước khi dùng token trong link

        // ✅ Dựng link dựa trên app.base-url (MailService.buildUrl đã đọc từ application.properties/ENV)
        String verifyLink = mailService.buildUrl("/verify", "token", t.getToken());

        String html = """
            <p>Xin chào %s,</p>
            <p>Vui lòng xác minh tài khoản EAUT Library của bạn bằng cách bấm vào liên kết bên dưới:</p>
            <p><a href="%s">%s</a></p>
            <p>Liên kết sẽ hết hạn vào: %s</p>
            <p>Nếu bạn không đăng ký, hãy bỏ qua email này.</p>
            """.formatted(u.getEmail(), verifyLink, verifyLink, t.getExpiresAt());

        mailService.sendHtml(u.getEmail(), "Xác nhận email - EAUT Library", html);
    }

    /** Xác thực token. Trả về User nếu hợp lệ, đồng thời bật cờ emailVerified và xoá token. */
    @Transactional
    public User verifyToken(String token) {
        if (token == null || token.isBlank())
            throw new IllegalArgumentException("Token không hợp lệ.");

        EmailVerificationToken t = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token không tồn tại hoặc đã được sử dụng."));

        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token đã hết hạn. Vui lòng yêu cầu gửi lại email xác nhận.");
        }

        User u = t.getUser();
        u.setEmailVerified(true);
        userRepo.save(u);

        // Xoá toàn bộ token của user (an toàn)
        tokenRepo.deleteByUserId(u.getId());
        return u;
    }

    /** Gửi lại email xác minh theo địa chỉ email/username */
    @Transactional
    public void resend(String emailOrUsername) {
        Optional<User> opt = userRepo.findByUsernameOrEmail(emailOrUsername, emailOrUsername);
        User u = opt.orElseThrow(() -> new IllegalArgumentException("Email/username không tồn tại."));
        if (u.isEmailVerified()) {
            throw new IllegalArgumentException("Tài khoản đã được xác minh trước đó.");
        }
        sendVerificationEmail(u);
    }

    /** Dọn token hết hạn mỗi đêm 2h */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanup() {
        tokenRepo.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(7));
    }
}
