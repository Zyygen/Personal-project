package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.ReservationService;
import com.example.demo.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class ReservationController {

    private final ReservationService reservationService;
    private final UserRepository userRepo;

    public ReservationController(ReservationService reservationService, UserRepository userRepo) {
        this.reservationService = reservationService;
        this.userRepo = userRepo;
    }

    private User currentUser(Authentication auth) {
        if (auth == null) return null;
        User u = userRepo.findByUsername(auth.getName());
        if (u == null) u = userRepo.findByEmail(auth.getName()).orElse(null);
        return u;
    }

    @PostMapping("/ticket/reserve/create/{bookId}")
    public String create(@PathVariable Long bookId,
                         Authentication auth,
                         RedirectAttributes ra) {
        User me = currentUser(auth);
        if (me == null) return "redirect:/signin";
        try {
            reservationService.create(me, bookId);
            ra.addFlashAttribute("message", "Đã đặt chỗ. Khi sách có lại, bạn sẽ được ưu tiên trong 24 giờ nếu chỉ còn 1 cuốn.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/borrow";
    }

    // ===== hủy đặt chỗ: POST /ticket/reserve/cancel/{id} =====
    @PostMapping("/ticket/reserve/cancel/{id}")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "back", required = false, defaultValue = "/borrow") String back,
                         Authentication auth,
                         RedirectAttributes ra) {
        User me = currentUser(auth);
        if (me == null) return "redirect:/signin";
        try {
            reservationService.cancelByOwner(me, id);
            ra.addFlashAttribute("message", "Đã hủy đặt chỗ #" + id);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        // back có thể là /borrow hoặc /return; chuẩn hóa
        if (!back.startsWith("/")) back = "/borrow";
        return "redirect:" + back;
    }

    // ===== API cho trang borrow poll thông báo READY =====
    @GetMapping("/api/reservations/my/ready")
    @ResponseBody
    public Map<String, Object> myReady(Authentication auth) {
        Map<String, Object> resp = new HashMap<>();
        User me = currentUser(auth);
        if (me == null) { resp.put("status", "UNAUTHORIZED"); return resp; }

        var readyOpt = reservationService.getOrPromoteReadyForUser(me.getId());
        if (readyOpt.isEmpty()) {
            resp.put("status", "NONE");
            return resp;
        }
        var r = readyOpt.get();
        resp.put("status", "READY");
        resp.put("reservationId", r.reservationId());
        resp.put("bookId", r.bookId());
        resp.put("bookTitle", r.bookTitle());
        resp.put("expireAt", r.expireAt());
        return resp;
    }
}
