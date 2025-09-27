package com.example.demo.controller;

import com.example.demo.model.BorrowTicket;
import com.example.demo.model.ReturnTicket;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowTicketRepository;
import com.example.demo.repository.ReturnTicketRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class QrHistoryController {

    private final BorrowTicketRepository borrowRepo;
    private final ReturnTicketRepository returnRepo;
    private final UserRepository userRepo;

    public QrHistoryController(BorrowTicketRepository b, ReturnTicketRepository r, UserRepository u) {
        this.borrowRepo = b; this.returnRepo = r; this.userRepo = u;
    }
    
    @GetMapping("/user/qr_history")
    public String history(Authentication auth,
                          @RequestParam(defaultValue = "0") int pageBP, // borrow pending
                          @RequestParam(defaultValue = "0") int pageBD, // borrow done
                          @RequestParam(defaultValue = "0") int pageRP, // return pending
                          @RequestParam(defaultValue = "0") int pageRD, // return done
                          Model model) {
        if (auth == null) return "redirect:/signin";
        User user = userRepo.findByUsername(auth.getName());
        if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) return "redirect:/signin";

        Long uid = user.getId();
        Sort sort = Sort.by(Sort.Direction.DESC, "requestedAt");

        Page<BorrowTicket> borrowPending =
            borrowRepo.findByUserIdAndStatus(uid, BorrowTicket.TicketStatus.PENDING,
                                             PageRequest.of(pageBP, 5, sort));
        Page<BorrowTicket> borrowDone =
            borrowRepo.findByUserIdAndStatusNot(uid, BorrowTicket.TicketStatus.PENDING,
                                                PageRequest.of(pageBD, 5, sort));

        Page<ReturnTicket> returnPending =
            returnRepo.findByBorrow_User_IdAndStatus(uid, ReturnTicket.TicketStatus.PENDING,
                                                     PageRequest.of(pageRP, 5, sort));
        Page<ReturnTicket> returnDone =
            returnRepo.findByBorrow_User_IdAndStatusNot(uid, ReturnTicket.TicketStatus.PENDING,
                                                        PageRequest.of(pageRD, 5, sort));

        model.addAttribute("borrowPending", borrowPending);
        model.addAttribute("borrowDone",    borrowDone);
        model.addAttribute("returnPending", returnPending);
        model.addAttribute("returnDone",    returnDone);
        return "user/qr_history";
    }
}