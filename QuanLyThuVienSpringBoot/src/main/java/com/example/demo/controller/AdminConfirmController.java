package com.example.demo.controller;

import com.example.demo.service.ConfirmService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/confirm")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfirmController {

    private final ConfirmService confirmService;

    public AdminConfirmController(ConfirmService confirmService) {
        this.confirmService = confirmService;
    }

    /** Trang quét/nhập mã để xác nhận mượn/trả */
    @GetMapping
    public String page(Model model) {
        return "admin/confirm";
    }

    /** Xác nhận CHO MƯỢN bằng mã (code trong QR hoặc nhập tay) */
    @PostMapping("/lend")
    public String confirmLend(@RequestParam("code") String code,
                              RedirectAttributes ra) {
        try {
            var result = confirmService.confirmLendByCode(code);
            ra.addFlashAttribute("message",
                "Đã xác nhận cho mượn. BorrowId=" + result.borrowId()
                + " | Sách: " + result.bookTitle()
                + " | Hạn trả: " + result.dueDate());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi xác nhận mượn: " + e.getMessage());
        }
        return "redirect:/admin/confirm";
    }

    @PostMapping("/return") 
    public String confirmReturn(@RequestParam("code") String code,
                                RedirectAttributes ra) {
        try {
            var r = confirmService.confirmReturnByCode(code);

            String title = (r.bookTitle() == null || r.bookTitle().isBlank())
                    ? "(không rõ)" : r.bookTitle();

            String amount = (r.fineAmount() != null && r.fineAmount().signum() > 0)
                    ? java.text.NumberFormat
                        .getCurrencyInstance(new java.util.Locale("vi","VN"))
                        .format(r.fineAmount())
                    : "0 ₫";

            ra.addFlashAttribute("message",
                    "Đã nhận lại sách. BorrowId=" + r.borrowId()
                    + " | Sách: " + title
                    + " | Phí đã thanh toán: " + amount);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi xác nhận trả: " + e.getMessage());
        }
        return "redirect:/admin/confirm";
    }
}
