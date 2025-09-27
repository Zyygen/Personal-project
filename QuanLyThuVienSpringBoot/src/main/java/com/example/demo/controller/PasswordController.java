package com.example.demo.controller;

import com.example.demo.model.PasswordResetToken;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PasswordResetService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordController {

    private final PasswordResetService resetService;
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    public PasswordController(PasswordResetService resetService, UserRepository userRepo, PasswordEncoder encoder) {
        this.resetService = resetService;
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    /** Hiển thị form quên mật khẩu */
    @GetMapping("/forgot-password")
    public String showForgotPassword(@RequestParam(value = "sent", required = false) String sent, Model model) {
        if (sent != null) {
            model.addAttribute("message", "Chúng tôi đã gửi hướng dẫn đặt lại mật khẩu.");
        }
        return "auth/forgot_password";
    }

    /** Nhận email và gửi mail chứa link reset */
    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam("email") String email, RedirectAttributes ra) {
        resetService.sendResetLink(email);
        ra.addAttribute("sent", "1");
        return "redirect:/forgot-password";
    }

    /** Người dùng bấm link trong mail -> đến trang đặt lại */
    @GetMapping("/reset-password")
    public String showReset(@RequestParam("token") String token, Model model) {
        try {
            PasswordResetToken t = resetService.validateTokenOrThrow(token);
            model.addAttribute("token", t.getToken());
            return "auth/reset_password";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/forgot_password";
        }
    }

    /** Xử lý đặt lại mật khẩu */
    @PostMapping("/reset-password")
    public String doReset(@RequestParam("token") String token,
                          @RequestParam(name = "password", required = false) String password,
                          @RequestParam(name = "newPassword", required = false) String newPassword,
                          @RequestParam(name = "confirm", required = false) String confirm,
                          RedirectAttributes ra,
                          Model model) {

        String pwd = (password != null && !password.isBlank()) ? password : newPassword;

        if (pwd == null || pwd.isBlank()) {
            model.addAttribute("error", "Vui lòng nhập mật khẩu mới.");
            model.addAttribute("token", token);
            return "auth/reset_password";
        }
        if (confirm == null || confirm.isBlank()) {
            model.addAttribute("error", "Vui lòng nhập xác nhận mật khẩu.");
            model.addAttribute("token", token);
            return "auth/reset_password";
        }
        if (pwd.length() < 8) {
            model.addAttribute("error", "Mật khẩu tối thiểu 8 ký tự.");
            model.addAttribute("token", token);
            return "auth/reset_password";
        }
        if (!pwd.equals(confirm)) {
            model.addAttribute("error", "Xác nhận mật khẩu không khớp.");
            model.addAttribute("token", token);
            return "auth/reset_password";
        }

        try {
            PasswordResetToken t = resetService.validateTokenOrThrow(token);
            User u = t.getUser();
            u.setPassword(encoder.encode(pwd));
            userRepo.save(u);
            resetService.markUsed(token);

            ra.addFlashAttribute("message", "Đặt lại mật khẩu thành công. Vui lòng đăng nhập.");
            return "redirect:/signin";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("token", token);
            return "auth/reset_password";
        }
    }
}
