package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.VerificationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthExtraController {

    private final VerificationService verificationService;

    public AuthExtraController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /** Người dùng click vào link /verify?token=... trong email */
    @GetMapping("/verify")
    public String verify(@RequestParam("token") String token, RedirectAttributes ra) {
        try {
            User u = verificationService.verifyToken(token);
            ra.addFlashAttribute("message",
                    "Tài khoản " + u.getEmail() + " đã được kích hoạt thành công. Bạn có thể đăng nhập.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/signin";
    }

    /** Form gửi lại email xác minh */
    @GetMapping("/verify/resend")
    public String showResendForm() {
        return "auth/resend_verification";
    }

    /** Xử lý gửi lại email xác minh */
    @PostMapping("/verify/resend")
    public String resend(@RequestParam("email") String email, RedirectAttributes ra) {
        try {
            verificationService.resend(email);
            ra.addFlashAttribute("message", "Đã gửi lại email xác nhận tới " + email + ". Vui lòng kiểm tra hộp thư.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/verify/resend";
    }
}
