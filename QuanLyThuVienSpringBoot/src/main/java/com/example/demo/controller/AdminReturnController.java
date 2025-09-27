package com.example.demo.controller;

import com.example.demo.repository.UserRepository;
import com.example.demo.service.ReturnTicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/return")
public class AdminReturnController {
  private final ReturnTicketService service;
  private final UserRepository userRepo;

  public AdminReturnController(ReturnTicketService s, UserRepository ur) {
    this.service=s; this.userRepo=ur;
  }

  @GetMapping("/scan")
  public String scan(@RequestParam String token, Model model) {
    model.addAttribute("token", token);
    return "admin_return_scan";
  }

  @PostMapping("/confirm")
  public String confirm(@RequestParam String token,
                        @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
                        Model model) {
    var admin = userRepo.findByEmail(principal.getUsername()).orElseThrow();
    var b = service.confirmByAdmin(token, admin);
    model.addAttribute("message", "Đã xác nhận trả: " + b.getBook().getTitle() + " từ " + b.getUser().getEmail());
    return "admin_return_scan_result";
  }
}
