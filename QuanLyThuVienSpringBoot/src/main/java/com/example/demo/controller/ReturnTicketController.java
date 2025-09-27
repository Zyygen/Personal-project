package com.example.demo.controller;

import com.example.demo.model.Borrow;
import com.example.demo.model.ReturnTicket;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.QrService;
import com.example.demo.service.ReturnTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ReturnTicketController {

  private final ReturnTicketService service;
  private final BorrowRepository borrowRepo;
  private final UserRepository userRepo;
  private final QrService qrService;

  @Value("${APP_BASE_URL:}")
  private String baseUrl;

  public ReturnTicketController(ReturnTicketService service,
                                BorrowRepository borrowRepo,
                                UserRepository userRepo,
                                QrService qrService) {
    this.service = service;
    this.borrowRepo = borrowRepo;
    this.userRepo = userRepo;
    this.qrService = qrService;
  }

  /* ===== Trang trả: đang mượn + đã trả ===== */
  @Value("${library.finePerDay:5000}")
  private long finePerDay;

  @Value("${library.freeDays:0}")
  private int freeDays;
  @GetMapping("/return")
  public String showReturn(Authentication auth, Model model) {
      if (auth == null) return "redirect:/login";

      User user = userRepo.findByUsername(auth.getName());
      if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
      if (user == null) return "redirect:/login";

      Long uid = user.getId();
      var borrowed = borrowRepo.findByUserIdAndReturnDateIsNull(uid);
      var returned = borrowRepo.findByUserIdAndReturnDateIsNotNullOrderByReturnDateDesc(uid);

      // ===== Khóa theo 24h tròn =====
      final LocalDateTime now = LocalDateTime.now();
      final long freeHours = (long) freeDays * 24L;

      boolean locked = borrowed.stream().anyMatch(b -> {
          if (b.getDueDate() == null) return false;

          long hoursLate = Duration.between(b.getDueDate(), now).toHours();
          if (hoursLate <= 0) return false;                 // chưa đến hạn
          long effective = Math.max(0L, hoursLate - freeHours);
          long overdueDays = effective / 24L;               // đủ 24h mới tính 1 ngày

          if (overdueDays < 1) return false;                // chưa quá hạn tròn ngày

          int qty = (b.getAmount() != null && b.getAmount() > 0) ? b.getAmount() : 1;
          BigDecimal fineNow = BigDecimal.valueOf(overdueDays * 1L * finePerDay * qty);

          BigDecimal paid = (b.getFinePaidTotal() == null) ? BigDecimal.ZERO : b.getFinePaidTotal();
          return paid.compareTo(fineNow) < 0;               // còn thiếu tiền phạt -> khóa
      });

      model.addAttribute("borrowedBooks", borrowed);
      model.addAttribute("returnedBooks", returned);
      model.addAttribute("locked", locked);
      model.addAttribute("finePerDay", finePerDay);
      model.addAttribute("freeDays", freeDays);

      return "user/return";
  }

  /* ===== Tạo vé trả: PRG để không tạo lại khi F5 ===== */
  @PostMapping("/ticket/return/create/{borrowId}")
  public String create(@PathVariable Long borrowId,
                       Authentication auth, RedirectAttributes ra) {
    if (auth == null) return "redirect:/login";
    User user = userRepo.findByUsername(auth.getName());
    if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
    if (user == null) return "redirect:/login";

    try {
      Borrow b = borrowRepo.findById(borrowId).orElseThrow();
      if (!b.getUser().getId().equals(user.getId())) {
        ra.addFlashAttribute("error", "Bạn không có quyền tạo phiếu trả này.");
        return "redirect:/return";
      }
      if (b.getReturnDate() != null) {
        ra.addFlashAttribute("error", "Phiếu mượn này đã được trả.");
        return "redirect:/return";
      }

      var view = service.createTicket(borrowId);
      return "redirect:/ticket/return/" + view.ticket().getId() + "?back=return";

    } catch (Exception e) {
      ra.addFlashAttribute("error", e.getMessage());
      return "redirect:/return";
    }
  }

  @Transactional(readOnly = true)
  @GetMapping("/ticket/return/{id}")
  public String viewReturnTicket(@PathVariable Long id,
                                 @RequestParam(value = "back", required = false) String back,
                                 Authentication auth,
                                 Model model,
                                 RedirectAttributes ra) {
    if (auth == null) return "redirect:/login";
    User user = userRepo.findByUsername(auth.getName());
    if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
    if (user == null) return "redirect:/login";

    var t = service.findById(id).orElse(null);
    if (t == null) {
      ra.addFlashAttribute("error", "Vé không tồn tại.");
      return "redirect:/user/qr_history";
    }
    if (t.getBorrow() == null || t.getBorrow().getUser() == null
        || !t.getBorrow().getUser().getId().equals(user.getId())) {
      ra.addFlashAttribute("error", "Bạn không có quyền xem vé này.");
      return "redirect:/user/qr_history";
    }

    String scanUrl = normalizeBaseUrl(baseUrl) + "/admin/return/scan?token=" + t.getToken();
    String qr = qrService.toDataUriPng(scanUrl, 280);

    model.addAttribute("requestedEpoch", toEpochMillis(t.getRequestedAt()));
    model.addAttribute("expiresEpoch",   toEpochMillis(t.getExpiresAt()));

    model.addAttribute("ticket", t);
    model.addAttribute("qr", qr);
    model.addAttribute("back", back); 

    if (t.getStatus() == ReturnTicket.TicketStatus.CONFIRMED) {
      model.addAttribute("confirmSuccess", true);
    }
    return "user/ticket_return";
  }

  @PostMapping("/ticket/return/cancel/{id}")
  public String cancelReturn(@PathVariable Long id,
                             Authentication auth,
                             @RequestParam(value = "back", required = false) String back,
                             RedirectAttributes ra) {
    if (auth == null) return "redirect:/login";
    User user = userRepo.findByUsername(auth.getName());
    if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
    if (user == null) return "redirect:/login";

    try {
      service.cancelByOwner(user, id);
      ra.addFlashAttribute("message", "Đã hủy vé trả #" + id);
    } catch (Exception e) {
      ra.addFlashAttribute("error", e.getMessage());
    }
    return "history".equalsIgnoreCase(back)
        ? "redirect:/user/qr_history"
        : "redirect:/return";
  }

  @Transactional(readOnly = true)
  @GetMapping("/api/tickets/return/{id}/status")
  @ResponseBody
  public Map<String, Object> returnTicketStatus(@PathVariable Long id, Authentication auth) {
    Map<String, Object> resp = new HashMap<>();
    var tOpt = service.findById(id);
    if (tOpt.isEmpty()) { resp.put("status", "NOT_FOUND"); return resp; }
    var t = tOpt.get();

    if (auth == null) { resp.put("status", "UNAUTHORIZED"); return resp; }
    User user = userRepo.findByUsername(auth.getName());
    if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
    if (user == null || t.getBorrow() == null || t.getBorrow().getUser() == null
        || !t.getBorrow().getUser().getId().equals(user.getId())) {
      resp.put("status", "FORBIDDEN"); return resp;
    }

    resp.put("status", t.getStatus().name());
    resp.put("confirmedAt", t.getConfirmedAt());
    return resp;
  }

  /* ===== Helpers ===== */
  private long toEpochMillis(LocalDateTime dt) {
    if (dt == null) return 0L;
    return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  private String normalizeBaseUrl(String url) {
    if (url == null || url.isBlank()) return "";
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
