package com.example.demo.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String configuredFrom;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /* ===================== URL helpers ===================== */

    /** Dựng URL: buildUrl("/reset-password", Map.of("token", "...")) */
    public String buildUrl(String path, Map<String, ?> queryParams) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(appBaseUrl)
                .path(path.startsWith("/") ? path : "/" + path);
        if (queryParams != null) {
            queryParams.forEach((k, v) -> { if (v != null) b.queryParam(k, v); });
        }
        return b.toUriString();
    }

    /** Dựng URL: buildUrl("/verify", "token", "...") */
    public String buildUrl(String path, String name, Object value) {
        return UriComponentsBuilder.fromHttpUrl(appBaseUrl)
                .path(path.startsWith("/") ? path : "/" + path)
                .queryParam(name, value)
                .toUriString();
    }

    /* ===================== Mail helpers ===================== */

    private String resolveFrom() {
        return (configuredFrom == null || configuredFrom.isBlank()) ? defaultFrom : configuredFrom;
    }

    /** Gửi text thuần */
    public void send(String to, String subject, String body) {
        if (!mailEnabled) {
            System.out.println("[MAIL-FAKE] To: " + to);
            System.out.println("[MAIL-FAKE] Subject: " + subject);
            System.out.println("[MAIL-FAKE] Body:\n" + body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(resolveFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (MailException ex) {
            System.err.println("[MAIL-ERROR] send(text) -> " + ex.getMessage());
            throw ex;
        }
    }

    /** Gửi HTML */
    public void sendHtml(String to, String subject, String html) {
        if (!mailEnabled) {
            System.out.println("[MAIL-FAKE-HTML] To: " + to);
            System.out.println("[MAIL-FAKE-HTML] Subject: " + subject);
            System.out.println("[MAIL-FAKE-HTML] HTML:\n" + html);
            return;
        }
        try {
            MimeMessage mm = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mm, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(resolveFrom());               // Với Gmail nên trùng spring.mail.username
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);                 // true = HTML
            mailSender.send(mm);
        } catch (Exception ex) {                         // gộp để đơn giản
            System.err.println("[MAIL-ERROR] send(html) -> " + ex.getMessage());
            throw new RuntimeException("Send mail failed", ex);
        }
    }
}
