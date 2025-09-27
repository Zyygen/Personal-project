package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final VerificationService verificationService;

    public AuthController(UserRepository userRepo, PasswordEncoder encoder, VerificationService verificationService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.verificationService = verificationService;
    }

    @GetMapping("/signin")
    public String signin(Model model,
                         @RequestParam(value = "logout", required = false) Boolean logout) {
        return "signin";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    // ===== ĐĂNG KÝ =====
    @PostMapping("/signup")
    public String doSignup(@RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String fullName,
                           @RequestParam(name = "dob", required = false) String dobStr,
                           @RequestParam(name = "phone", required = false) String phone,
                           @RequestParam(name = "occupation", required = false) String occupation,
                           @RequestParam(required = false) String studentCode,
                           @RequestParam(required = false) String cccd,
                           RedirectAttributes ra,
                           Model model) {

        // ---- Email
        if (email == null || email.isBlank() ||
            !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return fail(model, "Email không hợp lệ.", "email",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }
        if (userRepo.findByEmail(email).isPresent()) {
            return fail(model, "Email đã được đăng ký.", "email",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }

        // ---- Mật khẩu
        if (password == null || password.length() < 8) {
            return fail(model, "Mật khẩu tối thiểu 8 ký tự.", "password",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }

        // ---- Họ tên
        if (fullName == null || fullName.trim().isEmpty()) {
            return fail(model, "Vui lòng nhập Họ và tên.", "fullName",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }

        // ---- SĐT
        if (phone == null || !phone.matches("^\\+?\\d{9,15}$")) {
            return fail(model, "Số điện thoại không hợp lệ (9–15 chữ số, có thể có dấu +).", "phone",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }

        // ---- Ngày sinh (>=18)
        java.time.LocalDate dob;
        if (dobStr == null || dobStr.isBlank()) {
            return fail(model, "Vui lòng chọn ngày sinh.", "dob",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }
        try {
            dob = java.time.LocalDate.parse(dobStr);
        } catch (java.time.format.DateTimeParseException e) {
            return fail(model, "Định dạng ngày sinh không hợp lệ (yyyy-MM-dd).", "dob",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }
        int age = java.time.Period.between(dob, java.time.LocalDate.now()).getYears();
        if (age < 18) {
            return fail(model, "Bạn phải đủ 18 tuổi tính đến hôm nay.", "dob",
                    email, fullName, dobStr, phone, occupation, studentCode, cccd);
        }

        // ---- Nghề nghiệp + Mã định danh
        User.Occupation occEnum = User.Occupation.OTHER;
        if (occupation != null && !occupation.isBlank()) {
            try { occEnum = User.Occupation.valueOf(occupation.trim().toUpperCase()); }
            catch (Exception ignored) { occEnum = User.Occupation.OTHER; }
        }
        if (occEnum == User.Occupation.STUDENT) {
            if (studentCode == null || studentCode.trim().isEmpty()) {
                return fail(model, "Sinh viên phải nhập Mã sinh viên.", "studentCode",
                        email, fullName, dobStr, phone, occupation, studentCode, cccd);
            }
        } else { // LECTURER / OTHER
            if (cccd == null || cccd.trim().isEmpty()) {
                return fail(model, "Giảng viên/Khác phải nhập CCCD.", "cccd",
                        email, fullName, dobStr, phone, occupation, studentCode, cccd);
            }
        }

        // ---- Lưu user
        User u = new User();
        u.setEmail(email);
        u.setUsername(email);
        u.setPassword(encoder.encode(password));
        u.setFullName(fullName.trim());
        u.setDob(dob);
        u.setPhone(phone);
        u.setOccupation(occEnum);
        u.setStudentCode(occEnum == User.Occupation.STUDENT ? studentCode : null);
        u.setCccd(occEnum == User.Occupation.STUDENT ? null : cccd);
        u.setRole("ROLE_USER");
        u.setStatus(User.AccountStatus.ACTIVE);
        u.setEmailVerified(false);
        userRepo.save(u);

        verificationService.sendVerificationEmail(u);
        ra.addFlashAttribute("message", "Đăng ký thành công! Vui lòng kiểm tra email để xác nhận tài khoản.");
        return "redirect:/signin";
    }

    // ===== ĐĂNG XUẤT (cho form POST /logout) =====
    @PostMapping("/logout")
    public String doLogout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return "redirect:/signin?logout=true";
    }

    /** Push 1 lỗi + giữ lại dữ liệu + chỉ định field để focus */
    private String fail(Model model, String message, String errorField,
                        String email, String fullName, String dobStr, String phone,
                        String occupation, String studentCode, String cccd) {
        model.addAttribute("error", message);
        model.addAttribute("errorField", errorField);
        model.addAttribute("email", email);
        model.addAttribute("fullName", fullName);
        model.addAttribute("dob", dobStr);
        model.addAttribute("phone", phone);
        model.addAttribute("occupation", occupation);
        model.addAttribute("studentCode", studentCode);
        model.addAttribute("cccd", cccd);
        return "signup";
    }
}
