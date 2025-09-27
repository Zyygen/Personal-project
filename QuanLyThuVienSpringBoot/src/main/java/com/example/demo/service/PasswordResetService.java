package com.example.demo.service;

import com.example.demo.model.PasswordResetToken;
import com.example.demo.model.PasswordResetToken.Purpose;
import com.example.demo.model.User;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final MailService mailService;
    private final PasswordEncoder encoder;

    @Value("${app.password-reset.expire-hours:2}")
    private long expireHours;

    public PasswordResetService(PasswordResetTokenRepository tokenRepo,
                                UserRepository userRepo,
                                MailService mailService,
                                PasswordEncoder encoder) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.mailService = mailService;
        this.encoder = encoder;
    }

    @Transactional
    public void sendResetLink(String email) {
        Optional<User> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) return; 

        User user = userOpt.get();

        PasswordResetToken t = new PasswordResetToken();
        t.setToken(randomToken());
        t.setUser(user);
        t.setPurpose(Purpose.RESET);
        t.setCreatedAt(LocalDateTime.now());
        t.setExpiresAt(LocalDateTime.now().plusHours(expireHours));
        tokenRepo.saveAndFlush(t);

        String link = mailService.buildUrl("/reset-password", "token", t.getToken());
        String html = """
            <p>Xin chào <b>%s</b>,</p>
            <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>
            <p>Nhấp vào liên kết dưới đây để đặt lại mật khẩu (hết hạn sau %d giờ):</p>
            <p><a href="%s">%s</a></p>
            <p>Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
        """.formatted(user.getEmail(), expireHours, link, link);

        mailService.sendHtml(user.getEmail(), "Đặt lại mật khẩu", html);
    }

    @Transactional
    public void sendChangePasswordLink(User user) {
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(randomToken());
        t.setUser(user);
        t.setPurpose(Purpose.CHANGE);
        t.setCreatedAt(LocalDateTime.now());
        t.setExpiresAt(LocalDateTime.now().plusHours(expireHours));
        tokenRepo.saveAndFlush(t);

        String link = mailService.buildUrl("/change-password/confirm", "token", t.getToken());
        String html = """
            <p>Xin chào <b>%s</b>,</p>
            <p>Bạn vừa yêu cầu đổi mật khẩu. Nhấn vào liên kết dưới đây để đặt mật khẩu mới (hết hạn sau %d giờ):</p>
            <p><a href="%s">%s</a></p>
            <p>Nếu không phải bạn, hãy bỏ qua email này.</p>
        """.formatted(user.getEmail(), expireHours, link, link);

        mailService.sendHtml(user.getEmail(), "Xác nhận đổi mật khẩu", html);
    }

    @Transactional(readOnly = true)
    public PasswordResetToken validateTokenOrThrow(String token) {
        PasswordResetToken t = tokenRepo.findByTokenFetchUser(token)
                .orElseThrow(() -> new IllegalArgumentException("Token không hợp lệ."));
        if (t.isUsed())    throw new IllegalStateException("Token đã được sử dụng.");
        if (t.isExpired()) throw new IllegalStateException("Token đã hết hạn.");
        return t;
    }

    @Transactional(readOnly = true)
    public PasswordResetToken validateTokenOrThrowWithPurpose(String token, Purpose expected) {
        PasswordResetToken t = validateTokenOrThrow(token);
        if (t.getPurpose() != expected) {
            throw new IllegalStateException("Token không đúng mục đích.");
        }
        return t;
    }

    @Transactional
    public void finishPasswordChange(User userWithEncodedPassword, String token) {
        userRepo.save(userWithEncodedPassword);
        markUsed(token);
    }

    @Transactional
    public void markUsed(String token) {
        tokenRepo.findByToken(token).ifPresent(t -> {
            t.setUsedAt(LocalDateTime.now());
            tokenRepo.save(t);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Scheduled(cron = "0 0/10 * * * *")
    public void cleanupExpired() {
      int deleted = tokenRepo.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
