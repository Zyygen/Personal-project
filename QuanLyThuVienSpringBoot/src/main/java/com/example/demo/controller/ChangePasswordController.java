package com.example.demo.controller;

import com.example.demo.model.PasswordResetToken;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PasswordResetService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChangePasswordController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final PasswordResetService resetService;

    public ChangePasswordController(UserRepository userRepo,
                                    PasswordEncoder encoder,
                                    PasswordResetService resetService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.resetService = resetService;
    }

    /** Trang yêu cầu đổi mật khẩu (chỉ nhập mật khẩu hiện tại rồi gửi mail) */
    @GetMapping("/change-password")
    public String showChange(Authentication auth, Model model) {
        if (auth == null) return "redirect:/signin";
        return "auth/change_password"; // đúng với file bạn gửi
    }

    /** Nhận mật khẩu hiện tại, kiểm tra đúng -> gửi mail xác nhận đổi mật khẩu */
    @PostMapping("/change-password")
    public String startChange(@RequestParam("current") String currentPassword,
                              Authentication auth,
                              RedirectAttributes ra,
                              Model model) {
        if (auth == null) return "redirect:/signin";

        User me = userRepo.findByUsername(auth.getName());
        if (me == null) me = userRepo.findByEmail(auth.getName()).orElse(null);
        if (me == null) return "redirect:/signin";

        if (currentPassword == null || !encoder.matches(currentPassword, me.getPassword())) {
            model.addAttribute("error", "Mật khẩu hiện tại không đúng.");
            return "auth/change_password";
        }

        // Gửi mail xác nhận đổi mật khẩu (purpose = CHANGE)
        resetService.sendChangePasswordLink(me);

        // quay lại trang form và hiện thông báo bằng ?sent=1 (template của bạn đã hiển thị param.sent)
        ra.addAttribute("sent", "1");
        return "redirect:/change-password";
    }

    /** Người dùng bấm vào link trong email -> hiển thị form đặt mật khẩu mới (tái dùng view reset_password) */
    @GetMapping("/change-password/confirm")
    public String showConfirm(@RequestParam("token") String token, Model model) {
        try {
            PasswordResetToken t = resetService.validateTokenOrThrowWithPurpose(
                    token, PasswordResetToken.Purpose.CHANGE);
            model.addAttribute("token", t.getToken());
            return "auth/reset_password"; // dùng lại form đặt mật khẩu mới có sẵn
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/forgot_password"; // hoặc chuyển hướng chỗ khác tuỳ bạn
        }
    }

    /** Đặt mật khẩu mới sau khi xác nhận email */
    @PostMapping("/change-password/confirm")
    public String doConfirm(@RequestParam("token") String token,
                            @RequestParam("password") String password,
                            @RequestParam("confirm") String confirm,
                            RedirectAttributes ra,
                            Model model) {
        if (password == null || password.length() < 8) {
            model.addAttribute("error", "Mật khẩu mới tối thiểu 8 ký tự.");
            model.addAttribute("token", token);
            return "auth/reset_password";
        }
        if (!password.equals(confirm)) {
            model.addAttribute("error", "Xác nhận mật khẩu không khớp.");
            model.addAttribute("token", token);
            return "auth/reset_password";
        }

        try {
            PasswordResetToken t = resetService.validateTokenOrThrowWithPurpose(
                    token, PasswordResetToken.Purpose.CHANGE);

            User u = t.getUser();
            u.setPassword(encoder.encode(password));
            // lưu user & đánh dấu token đã dùng
            resetService.finishPasswordChange(u, token);

            ra.addFlashAttribute("message", "Đổi mật khẩu thành công. Vui lòng đăng nhập.");
            return "redirect:/signin";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("token", token);
            return "auth/reset_password";
        }
    }
}
