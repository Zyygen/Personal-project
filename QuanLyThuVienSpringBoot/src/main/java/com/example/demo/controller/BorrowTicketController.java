package com.example.demo.controller;

import com.example.demo.model.Borrow;
import com.example.demo.model.BorrowTicket;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.BorrowTicketRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BookService;
import com.example.demo.service.BorrowService;
import com.example.demo.service.BorrowTicketService;
import com.example.demo.service.QrService;
import com.example.demo.service.ReservationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class BorrowTicketController {

    private final BorrowTicketService borrowTicketService;
    private final BookService bookService;
    private final UserRepository userRepo;
    private final BorrowRepository borrowRepo;
    private final BorrowService borrowService;
    private final BorrowTicketRepository borrowTicketRepo;
    private final QrService qrService;
    private final ReservationService reservationService;

    @Value("${APP_BASE_URL:}")
    private String baseUrl;

    public BorrowTicketController(BorrowTicketService borrowTicketService, ReservationService reservationService, BookService bookService,
                                  UserRepository userRepo, BorrowRepository borrowRepo, BorrowService borrowService,
                                  BorrowTicketRepository borrowTicketRepo, QrService qrService) {
        this.borrowTicketService = borrowTicketService;
        this.bookService = bookService;
        this.userRepo = userRepo;
        this.borrowRepo = borrowRepo;
        this.borrowService = borrowService;
        this.borrowTicketRepo = borrowTicketRepo;
        this.qrService = qrService;
        this.reservationService = reservationService;
    }

    @GetMapping("/borrow")
    public String showBorrow(@RequestParam(value = "bookId", required = false) Long bookId,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             Authentication auth,
                             Model model) {

        boolean isMember = false;
        int maxDays = 7;
        int limit   = 2;   // số cuốn tối đa cho tài khoản thường

        User user = null;
        if (auth != null) {
            user = userRepo.findByUsername(auth.getName());
            if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
            if (user != null) {
                isMember = Boolean.TRUE.equals(user.isMember());
                maxDays  = isMember ? 14 : 7;
                limit    = isMember ? 5  : 2;
            }
        }

        model.addAttribute("isMember", isMember);
        model.addAttribute("maxDays", maxDays);
        model.addAttribute("limit", limit);
        model.addAttribute("userMaxDays", maxDays);
        model.addAttribute("maxBooks", limit); // alias

        if (bookId != null) {
            bookService.findById(bookId).ifPresent(b -> model.addAttribute("book", b));
        }

        // Phân trang
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, 10);

        // Tìm kiếm
        Page<com.example.demo.model.Book> books;
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            books = bookService.search(kw, pageable);
            model.addAttribute("keyword", kw);

            boolean empty = books == null || books.isEmpty();
            model.addAttribute("notFound", empty);
            if (empty) {
                model.addAttribute("noResultsMsg", "Không tìm thấy sách phù hợp với từ khóa \"" + kw + "\".");
            }
        } else {
            books = bookService.getBooksPage(pageable);
        }
        model.addAttribute("books", books);

        // Các phiếu mượn đang mở của user
        List<Borrow> borrowed = Collections.emptyList();
        if (user != null) {
            borrowed = borrowRepo.findOpenByUserFetchBookOrderByDue(user.getId());
            for (Borrow b : borrowed) {
                try { borrowService.calculateOverdue(b); } catch (Exception ignore) {}
                try { borrowService.annotateExtendability(b, isMember); } catch (Exception ignore) {}
            }
        }
        model.addAttribute("borrowedBooks", borrowed);

        return "user/borrow";
    }

    @PostMapping("/ticket/borrow/create/{bookId}")
    public String createBorrow(@PathVariable Long bookId,
                               @RequestParam(name="amount", defaultValue="1") int amount,
                               @RequestParam(name="days",   defaultValue="1") int days,
                               Authentication auth, RedirectAttributes ra) {
        if (auth == null) return "redirect:/signin";
        User user = userRepo.findByUsername(auth.getName());
        if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) return "redirect:/signin";

        // chặn theo luật giữ chỗ
        var gate = reservationService.assertBorrowAllowed(bookId, user.getId());
        if (!gate.allowed()) {
            ra.addFlashAttribute("error", gate.reason());
            return "redirect:/borrow";
        }

        // fallback nếu client gửi số <=0
        if (amount < 1) amount = 1;
        if (days   < 1) days   = 1;

        try {
            var tv = borrowTicketService.createTicket(user, bookId, amount, days);
            reservationService.markFulfilledIfAny(bookId, user.getId());
            return "redirect:/ticket/borrow/" + tv.ticket().getId() + "?back=/borrow";
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/borrow";
        }
    }


    // ✅ chỉ nhận số cho {id} để tránh /ticket/borrow/borrow khớp nhầm
    @GetMapping("/ticket/borrow/{id:\\d+}")
    public String viewBorrowTicket(@PathVariable Long id,
                                   @RequestParam(value = "back", required = false) String back,
                                   Authentication auth, Model model, RedirectAttributes ra) {
        if (auth == null) return "redirect:/signin";
        User user = userRepo.findByUsername(auth.getName());
        if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) return "redirect:/signin";

        // Nạp kèm BOOK (repo của bạn đã có findWithBookById)
        var tOpt = borrowTicketRepo.findWithBookById(id);
        if (tOpt.isEmpty()) {
            ra.addFlashAttribute("error", "Vé không tồn tại.");
            return "redirect:/user/qr_history";
        }
        var t = tOpt.get();
        if (t.getUser() == null || !t.getUser().getId().equals(user.getId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền xem vé này.");
            return "redirect:/user/qr_history";
        }

        String scanUrl = normalizeBaseUrl(baseUrl) + "/admin/ticket/scan?token=" + t.getToken();
        String qr = qrService.toDataUriPng(scanUrl, 280);

        model.addAttribute("ticket", t);
        model.addAttribute("qr", qr);
        model.addAttribute("back", back);

        // ✅ Đẩy sẵn dữ liệu để template KHÔNG đụng LAZY
        String bookTitle  = (t.getBook() != null) ? t.getBook().getTitle()  : null;
        String bookAuthor = (t.getBook() != null) ? t.getBook().getAuthor() : null;
        model.addAttribute("bookTitle", bookTitle);
        model.addAttribute("bookAuthor", bookAuthor);

        // ✅ Dùng user hiện tại (chính là chủ vé) để hiển thị, tránh dereference ticket.user
        model.addAttribute("viewerFullName", user.getFullName());
        model.addAttribute("viewerEmail", user.getEmail());

        if (t.getStatus() == BorrowTicket.TicketStatus.CONFIRMED) {
            model.addAttribute("confirmSuccess", true);
        }
        return "user/ticket_borrow";
    }

    // ✅ chỉ nhận số + normalize back an toàn
    @PostMapping("/ticket/borrow/cancel/{id:\\d+}")
    public String cancelBorrowTicket(@PathVariable Long id,
                                     @RequestParam(value = "back", required = false) String back,
                                     Authentication auth, RedirectAttributes ra) {
        if (auth == null) return "redirect:/signin";

        User user = userRepo.findByUsername(auth.getName());
        if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) return "redirect:/signin";

        try {
            borrowTicketService.cancelByOwner(user, id);
            ra.addFlashAttribute("message", "Đã hủy vé #" + id);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        // Giữ tương thích đường cũ: back=history → về trang lịch sử
        if ("history".equalsIgnoreCase(back)) return "redirect:/user/qr_history";
        return "redirect:" + normalizeBack(back);
    }

    @PostMapping("/borrow/{id}/extend")
    public String extend(@PathVariable Long id, @RequestParam("days") int days,
                         Authentication auth, RedirectAttributes ra) {
        if (auth == null) return "redirect:/signin";
        User user = userRepo.findByUsername(auth.getName());
        if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) return "redirect:/signin";
        try {
            borrowService.extendBorrow(id, days, user.getId(), Boolean.TRUE.equals(user.isMember()));
            ra.addFlashAttribute("message", "Gia hạn thành công +" + days + " ngày.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/borrow";
    }

    @GetMapping("/api/tickets/borrow/{id}/status")
    @ResponseBody
    public Map<String, Object> borrowTicketStatus(@PathVariable Long id, Authentication auth) {
        Map<String, Object> resp = new HashMap<>();
        var tOpt = borrowTicketRepo.findById(id);
        if (tOpt.isEmpty()) { resp.put("status", "NOT_FOUND"); return resp; }
        var t = tOpt.get();

        if (auth == null) { resp.put("status", "UNAUTHORIZED"); return resp; }
        User user = userRepo.findByUsername(auth.getName());
        if (user == null) user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null || t.getUser() == null || !t.getUser().getId().equals(user.getId())) {
            resp.put("status", "FORBIDDEN"); return resp;
        }

        resp.put("status", t.getStatus().name());
        resp.put("confirmedAt", t.getConfirmedAt());
        return resp;
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ✅ Chuẩn hóa 'back' an toàn: chỉ chấp nhận đường dẫn nội bộ, mặc định /borrow
    private String normalizeBack(String back) {
        if (back == null || back.isBlank() || "#".equals(back)) return "/borrow";
        back = back.trim();
        if (back.startsWith("http://") || back.startsWith("https://")) return "/borrow";
        if (!back.startsWith("/")) back = "/" + back.replaceFirst("^/+", "");
        return back.replaceFirst("^/+", "/");
    }
}
