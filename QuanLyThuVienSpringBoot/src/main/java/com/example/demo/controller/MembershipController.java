package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.MembershipService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
public class MembershipController {

    private final UserRepository userRepo;
    private final MembershipService membershipService;

    @Value("${app.membership.monthly.price:50000}")
    private long monthlyPrice;

    public MembershipController(UserRepository userRepo, MembershipService membershipService) {
        this.userRepo = userRepo;
        this.membershipService = membershipService;
    }

    @GetMapping("/user/member")
    public String member(Authentication auth, Model model) {
        if (auth == null) return "redirect:/signin";

        User u = userRepo.findByUsername(auth.getName());
        if (u == null) u = userRepo.findByEmail(auth.getName()).orElse(null);
        if (u == null) return "redirect:/signin";

        boolean isMember = membershipService.isMember(u);
        LocalDateTime until = u.getMemberUntil();
        LocalDateTime preview = membershipService.previewAfterMonthly(u);

        model.addAttribute("me", u);
        model.addAttribute("isMember", isMember);
        model.addAttribute("memberUntil", until);
        model.addAttribute("previewExpiry", preview);

        model.addAttribute("basicMaxBooks", 2);
        model.addAttribute("basicDays", 7);
        model.addAttribute("memberMaxBooks", 5);
        model.addAttribute("memberDays", 14);
        model.addAttribute("monthlyPrice", monthlyPrice);

        return "user/member";
    }

    // Return URL từ VNPay (dành cho user)
    @GetMapping({"/membership/vnpay-return", "/membership/return"})
    public String vnpReturn(@RequestParam Map<String,String> all, RedirectAttributes ra) {
        String code = all.getOrDefault("vnp_ResponseCode", null);
        if ("00".equals(code)) {
            ra.addFlashAttribute("message","Thanh toán VNPay đã hoàn tất. Hệ thống sẽ xác nhận qua IPN.");
        } else if (code != null) {
            ra.addFlashAttribute("error","Giao dịch không thành công (mã " + code + ").");
        } else {
            ra.addFlashAttribute("message","Đã nhận phản hồi từ VNPay. Vui lòng đợi hệ thống đối soát IPN.");
        }
        return "redirect:/user/member";
    }

    // Alias để giữ đường dẫn cũ nếu UI chưa đổi
    @PostMapping("/membership/checkout-vnpay")
    public String legacyCheckoutForward() {
        return "forward:/payment/membership";
    }
}
