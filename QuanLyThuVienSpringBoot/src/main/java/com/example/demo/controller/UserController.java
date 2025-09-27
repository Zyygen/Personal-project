package com.example.demo.controller;

import com.example.demo.model.Borrow;
import com.example.demo.model.BorrowTicket;
import com.example.demo.model.ReturnTicket;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.BorrowTicketRepository;
import com.example.demo.repository.ReturnTicketRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AccountGuardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Collections;

/**
 * Trang dành cho user: hồ sơ, danh sách mượn, trả sách, liên hệ...
 * Tối thiểu hoá để tránh lỗi biên dịch/view.
 */
@Controller
@RequestMapping("/user")
public class UserController {

	private final BorrowTicketRepository borrowRepo;
    private final ReturnTicketRepository returnRepo;
    private final UserRepository userRepo;

    public UserController(BorrowTicketRepository borrowRepo,
                             ReturnTicketRepository returnRepo,
                             UserRepository userRepo) {
        this.borrowRepo = borrowRepo;
        this.returnRepo = returnRepo;
        this.userRepo = userRepo;
    }

    /** Trang hồ sơ người dùng đang đăng nhập */
    @GetMapping("/profile")
    public String profile(Authentication auth, Model model) {
        if (auth == null) return "redirect:/signin";
        User me = userRepo.findByUsername(auth.getName());
        if (me == null) me = userRepo.findByEmail(auth.getName()).orElse(null);
        if (me == null) return "redirect:/signin";

        Long uid = me.getId();

        // nạp sẵn book/borrow.book để view không bị LazyInitializationException
        List<BorrowTicket> activeBorrows =
            borrowRepo.findByUserIdAndStatusOrderByRequestedAtDesc(
                uid, BorrowTicket.TicketStatus.PENDING); // hoặc CONFIRMED nếu bạn muốn

        List<ReturnTicket> activeReturns =
            returnRepo.findByBorrow_User_IdAndStatusOrderByRequestedAtDesc(
                uid, ReturnTicket.TicketStatus.PENDING);

        model.addAttribute("me", me);
        model.addAttribute("activeBorrowTickets", activeBorrows);
        model.addAttribute("activeReturnTickets", activeReturns);
        return "user/profile";
    }

    /** Trang liên hệ */
    @GetMapping("/contact")
    public String contact() {
        return "user/contact";
    }
}
