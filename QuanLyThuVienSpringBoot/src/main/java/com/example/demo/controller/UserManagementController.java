package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class UserManagementController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BorrowRepository borrowRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Hiển thị danh sách tài khoản USER
    @GetMapping("/user")
    public String manageUsers(Model model) {
        List<User> users = userRepository.findByRole("ROLE_USER");
        model.addAttribute("users", users);
        return "admin/user";
    }

    // Cập nhật tài khoản
    @PostMapping("/user/update/{id}")
    public String updateUser(@PathVariable Long id,
                             @RequestParam String username,
                             @RequestParam(required = false) String password,
                             RedirectAttributes ra) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty() || !"ROLE_USER".equals(opt.get().getRole())) {
            ra.addFlashAttribute("error", "Không tìm thấy tài khoản hoặc tài khoản không hợp lệ.");
            return "redirect:/admin/user";
        }

        User user = opt.get();
        user.setUsername(username);

        boolean changePwd = (password != null && !password.isBlank());
        if (changePwd) {
            user.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(user);

        ra.addFlashAttribute("message", "Cập nhật mật khẩu thành công.");
        return "redirect:/admin/user";
    }

    // Xoá tài khoản
    @Transactional
    @PostMapping("/user/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Optional<User> opt = userRepository.findById(id);
            if (opt.isEmpty() || !"ROLE_USER".equals(opt.get().getRole())) {
                ra.addFlashAttribute("error", "Không thể xoá: tài khoản không tồn tại hoặc không hợp lệ.");
                return "redirect:/admin/user";
            }

            User user = opt.get();

            //Chặn xoá nếu vẫn còn lịch sử/phiếu mượn.

            long c = borrowRepository.countByUserId(id);
            if (c > 0) {
               ra.addFlashAttribute("error", "Không thể xoá user vì còn dữ liệu mượn/trả liên quan.");
                 return "redirect:/admin/user";
            }

            // Hiện tại giữ nguyên logic xoá dữ liệu liên quan trước rồi xoá user:
            borrowRepository.deleteAllByUserId(id);
            userRepository.delete(user);

            ra.addFlashAttribute("message", "Đã xoá user thành công.");
        } catch (DataIntegrityViolationException ex) {
            // Trường hợp ràng buộc FK hoặc DB chặn xoá
            ra.addFlashAttribute("error", "Không thể xoá user do ràng buộc dữ liệu.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Không thể xoá user. Vui lòng thử lại.");
        }
        return "redirect:/admin/user";
    }
}
