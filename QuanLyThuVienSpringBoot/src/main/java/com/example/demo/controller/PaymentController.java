package com.example.demo.controller;

import com.example.demo.model.Borrow;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepo;
    private final BorrowRepository borrowRepo;

    public PaymentController(PaymentService paymentService,
                             UserRepository userRepo,
                             BorrowRepository borrowRepo) {
        this.paymentService = paymentService;
        this.userRepo = userRepo;
        this.borrowRepo = borrowRepo;
    }

 // ====== PHÍ PHẠT ======
    @PostMapping("/fine/{borrowId}")
    public String payFine(@PathVariable Long borrowId,
	            @RequestParam(value = "amount", required = false) String amountStr,
	            Authentication auth,
	            HttpServletRequest request,
	            RedirectAttributes ra) {
	Borrow b = borrowRepo.findById(borrowId).orElseThrow();
	if (auth == null) return "redirect:/signin";
	
	User me = userRepo.findByUsername(auth.getName());
	if (me == null) me = userRepo.findByEmail(auth.getName()).orElse(null);
	boolean isOwner = (b.getUser() != null && me != null && b.getUser().getId().equals(me.getId()));
	boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
	if (!isOwner && !isAdmin) {
	ra.addFlashAttribute("error", "Bạn không có quyền thanh toán phí cho phiếu này.");
	return "redirect:/return";
	}
	
	// Server tự tính tiền phạt hiện thời
	BigDecimal fineNow = paymentService.computeFine(b, LocalDateTime.now());
	if (fineNow.signum() <= 0) {
	ra.addFlashAttribute("message", "Không còn phí cần thanh toán.");
	return "redirect:/return";
	}
	
	try {
	String checkoutUrl = paymentService.createPaymentForFine(borrowId, request); // service sẽ nhúng borrowId vào TxnRef/OrderInfo
	return "redirect:" + checkoutUrl;
	} catch (IllegalStateException ex) {
	String msg = ex.getMessage();
	if ("Khong con phi can thanh toan.".equalsIgnoreCase(msg)) msg = "Không còn phí cần thanh toán.";
	ra.addFlashAttribute("message", msg != null ? msg : "Không thể tạo thanh toán.");
	return "redirect:/return";
		}
	}

    @GetMapping("/return")
    public String vnpayReturnFine(@RequestParam Map<String,String> qs,
                                  RedirectAttributes ra) {
        String code = qs.getOrDefault("vnp_ResponseCode", "");
        if ("00".equals(code)) {
            ra.addFlashAttribute("message", "Thanh toán phí phạt thành công. Hệ thống sẽ cập nhật sau khi nhận IPN.");
        } else {
            ra.addFlashAttribute("error", "Thanh toán không thành công (mã " + code + ").");
        }
        // Trả người dùng về trang Trả sách
        return "redirect:/return";
    }
    // Alias để giữ đường dẫn cũ nếu UI chưa đổi
    @PostMapping("/membership/checkout-vnpay")
    public String legacyCheckoutForward() {
        return "forward:/payment/membership";
    }

    // ====== THÀNH VIÊN ======
    @PostMapping("/membership")
    public String payMembership(@RequestParam(name = "months", defaultValue = "1") int months,
                                Authentication auth,
                                HttpServletRequest request,
                                RedirectAttributes ra) {
        if (auth == null) return "redirect:/signin";

        User me = userRepo.findByUsername(auth.getName());
        if (me == null) me = userRepo.findByEmail(auth.getName()).orElse(null);
        if (me == null) return "redirect:/signin";

        if (months < 1) months = 1;
        if (months > 12) months = 12;

        String url = paymentService.createPaymentForMembershipMonths(me.getId(), months, request);
        return "redirect:" + url;
    }

    // ====== IPN (VNPay gọi server-to-server) ======
    @RequestMapping(value = "/ipn", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public String paymentIpn(@RequestParam java.util.Map<String,String> all) {
        return paymentService.handleIpn(new java.util.HashMap<>(all));
    }
//    @PostMapping("/payment/ipn")
//    public ResponseEntity<String> vnpIpn(HttpServletRequest request) {
//        Map<String, String> fields = paymentService.extractParams(request);
//
//        // 1) Verify chữ ký/đơn hàng
//        if (!paymentService.verifyIpn(fields)) {
//            return ResponseEntity.status(400).body("INVALID");
//        }
//
//        // 2) Lấy trạng thái
//        String responseCode = fields.getOrDefault("vnp_ResponseCode", "");
//        String txnStatus    = fields.getOrDefault("vnp_TransactionStatus", "");
//        if (!"00".equals(responseCode) || !"00".equals(txnStatus)) {
//            return ResponseEntity.ok("IGNORED");
//        }
//
//        // 3) Lấy borrowId từ TxnRef hoặc OrderInfo
//        Long borrowId = paymentService.resolveBorrowId(fields); // tự bạn parse từ vnp_TxnRef/vnp_OrderInfo
//
//        // 4) Chuẩn hoá số tiền: VNPAY trả về VND * 100
//        long amountVnd = 0L;
//        try {
//            amountVnd = Long.parseLong(fields.getOrDefault("vnp_Amount", "0")) / 100L;
//        } catch (NumberFormatException ignore) {}
//
//        if (borrowId != null && amountVnd > 0) {
//            paymentService.applyFinePaid(borrowId, BigDecimal.valueOf(amountVnd));
//        }
//
//        return ResponseEntity.ok("OK");
//    }
}
