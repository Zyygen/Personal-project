package com.example.demo.service;

import com.example.demo.model.Borrow;
import com.example.demo.model.Payment;
import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.VnPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final BorrowRepository borrowRepo;
    private final UserRepository userRepo;
    private final MembershipService membershipService;

    // NEW: dùng để recalc theo quy tắc 24h tròn, freeDays, finePerDay hiện hành
    private final BorrowService borrowService;

    public PaymentService(PaymentRepository paymentRepo,
                          BorrowRepository borrowRepo,
                          UserRepository userRepo,
                          MembershipService membershipService,
                          BorrowService borrowService) {
        this.paymentRepo = paymentRepo;
        this.borrowRepo = borrowRepo;
        this.userRepo = userRepo;
        this.membershipService = membershipService;
        this.borrowService = borrowService;
    }

    @Value("${vnpay.tmnCode}")    private String tmnCode;
    @Value("${vnpay.hashSecret}") private String hashSecret;
    @Value("${vnpay.payUrl}")     private String payUrl;

    @Value("${vnpay.returnUrl:}")           private String defaultReturnUrl;
    @Value("${vnpay.returnUrlMembership:}") private String returnUrlMembership;

    @Value("${app.membership.monthly.price:50000}")
    private long monthlyPrice;

    @Value("${library.finePerDay:5000}")
    private long finePerDay;

    @Value("${library.freeDays:0}")
    private int freeDays;

    // =========================== FINE helper ============================
    /** Tính phí phạt tại thời điểm now (VND, BigDecimal) theo amount, freeDays hiện hành. */
    public BigDecimal computeFine(Borrow b, LocalDateTime now) {
        if (b == null || b.getDueDate() == null) return BigDecimal.ZERO;

        long rawLate = Duration.between(b.getDueDate(), now).toDays(); // có thể âm
        if (rawLate <= 0) return BigDecimal.ZERO;

        long lateDays = Math.max(0, rawLate - Math.max(0, freeDays));
        if (lateDays == 0) return BigDecimal.ZERO;

        long qty = (b.getAmount() == null ? 1 : b.getAmount());
        long vnd = lateDays * Math.max(0, finePerDay) * Math.max(1, qty);
        return (vnd <= 0) ? BigDecimal.ZERO : BigDecimal.valueOf(vnd);
    }

    // ----------------------------- helpers -----------------------------
    private String nowYmdHms() {
        return ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
    private String nowYmdHmsPlusMins(int mins) {
        return ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusMinutes(mins)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
    private String safe(String s) { return s == null ? "" : s.trim(); }
    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String ip = xff.split(",")[0].trim();
            if (!ip.isEmpty() && !ip.contains(":")) return ip;
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank() && !xri.contains(":")) return xri.trim();
        String raw = req.getRemoteAddr();
        return (raw != null && !raw.isBlank() && !raw.contains(":")) ? raw : "127.0.0.1";
    }
    private String base(HttpServletRequest req) {
        String proto = req.getHeader("X-Forwarded-Proto");
        String host  = req.getHeader("X-Forwarded-Host");
        if (proto != null && host != null) return proto + "://" + host;
        return req.getRequestURL().toString().replace(req.getRequestURI(), "");
    }
    private String abs(HttpServletRequest req, String path) {
        String ctx = (req.getContextPath() == null) ? "" : req.getContextPath();
        return base(req) + ctx + path;
    }
    private String returnUrlMembership(HttpServletRequest req) {
        if (returnUrlMembership != null && !returnUrlMembership.isBlank())
            return returnUrlMembership.trim();
        return abs(req, "/membership/vnpay-return");
    }
    private String returnUrlFine(HttpServletRequest req) {
        if (defaultReturnUrl != null && !defaultReturnUrl.isBlank())
            return defaultReturnUrl.trim();
        return abs(req, "/payment/return");
    }
    private int monthsFrom(BigDecimal amountVnd) {
        if (monthlyPrice <= 0) return 1;
        return amountVnd.divide(BigDecimal.valueOf(monthlyPrice), RoundingMode.DOWN).intValue();
    }

    // ============================== FINE (OVERLOAD) ==============================
    @Transactional
    public String createPaymentForFine(Long borrowId, BigDecimal amount, HttpServletRequest request) {
        Borrow b = borrowRepo.findById(borrowId).orElseThrow();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("Khong con phi can thanh toan.");
        }

        Payment p = new Payment();
        p.setBorrow(b);
        p.setUser(b.getUser());
        p.setAmount(amount);              // <-- SỐ TIỀN CHUẨN ĐÃ TÍNH SERVER-SIDE
        p.setCurrency("VND");
        p.setProvider("VNPAY");
        p.setMethod("VNPAY");
        p.setStatus("PENDING");
        p.setOrderInfo("Pay fine for borrow " + b.getId());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p.setIpnVerified(false);

        String ip   = clientIp(request);
        String cYmd = nowYmdHms();
        String eYmd = nowYmdHmsPlusMins(15);
        String vnpAmount = amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.DOWN).toPlainString();

        // vnp_TxnRef: chỉ chữ-số (length <= 34)
        String txnRef = "F" + b.getId() + (System.currentTimeMillis() / 1000L);
        if (txnRef.length() > 34) txnRef = txnRef.substring(0, 34);
        p.setTxnRef(txnRef);
        p.setVnpTxnRef(txnRef);
        paymentRepo.save(p);

        Map<String,String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", safe(tmnCode));
        params.put("vnp_Amount", vnpAmount);
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", p.getOrderInfo());
        params.put("vnp_OrderType", "billpayment");
        params.put("vnp_ReturnUrl", returnUrlFine(request));
        params.put("vnp_IpAddr", ip);
        params.put("vnp_CreateDate", cYmd);
        params.put("vnp_ExpireDate", eYmd);
        params.put("vnp_Locale", "vn");

        String url = VnPayUtil.buildPaymentUrl(payUrl, params, hashSecret.trim());
        return url;
    }

    // ============================== FINE (DELEGATE) ==============================
    @Transactional
    public String createPaymentForFine(Long borrowId, HttpServletRequest request) {
        Borrow b = borrowRepo.findById(borrowId).orElseThrow();
        BigDecimal fineNow  = computeFine(b, LocalDateTime.now());
        BigDecimal paid     = java.util.Optional.ofNullable(b.getFinePaidTotal()).orElse(BigDecimal.ZERO);
        BigDecimal remaining = fineNow.subtract(paid);
        if (remaining.signum() <= 0) {
            throw new IllegalStateException("Không còn phí cần thành toán.");
        }
        return createPaymentForFine(borrowId, remaining, request);
    }

    // ========================= MEMBERSHIP =========================
    @Transactional
    public String createPaymentForMembership(Long userId, long amountVnd, HttpServletRequest request) {
        if (amountVnd <= 0) throw new IllegalArgumentException("Số tiền không hợp lệ.");

        User u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        BigDecimal amount = BigDecimal.valueOf(amountVnd);

        Payment p = new Payment();
        p.setUser(u);
        p.setBorrow(null);
        p.setAmount(amount);
        p.setCurrency("VND");
        p.setProvider("VNPAY");
        p.setMethod("VNPAY");
        p.setStatus("PENDING");
        p.setOrderInfo("Membership for user " + userId);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p.setIpnVerified(false);

        String ts = String.valueOf(System.currentTimeMillis() / 1000L);
        String txnRef = ("M" + userId + ts);
        if (txnRef.length() > 34) txnRef = txnRef.substring(0, 34);
        p.setTxnRef(txnRef);
        p.setVnpTxnRef(txnRef);
        paymentRepo.save(p);

        String vnpAmount = amount.multiply(BigDecimal.valueOf(100))
                                 .setScale(0, RoundingMode.DOWN)
                                 .toPlainString();

        String cYmd = nowYmdHms();
        String eYmd = nowYmdHmsPlusMins(15);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode.trim());
        params.put("vnp_Amount", vnpAmount);
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", p.getOrderInfo());
        params.put("vnp_OrderType", "billpayment");
        params.put("vnp_ReturnUrl", returnUrlMembership(request));
        params.put("vnp_IpAddr", clientIp(request));
        params.put("vnp_CreateDate", cYmd);
        params.put("vnp_ExpireDate", eYmd);
        params.put("vnp_Locale", "vn");

        String url = VnPayUtil.buildPaymentUrl(payUrl, params, hashSecret.trim());
        return url;
    }

    @Transactional
    public String createPaymentForMembershipMonths(Long userId, int months, HttpServletRequest request) {
        if (months < 1) months = 1;
        if (months > 12) months = 12;
        long amountVnd = Math.multiplyExact((long) months, monthlyPrice);
        return createPaymentForMembership(userId, amountVnd, request);
    }

    // ============================== IPN ==============================
    @Transactional
    public String handleIpn(Map<String, String> qs) {
        // 1) Verify chữ ký (dual-mode: encoded & plain)
        if (!VnPayUtil.verifyFlexible(qs, hashSecret)) {
            return VnPayUtil.ipnResponse("97", "Invalid signature");
        }

        // 2) Tìm đơn theo TxnRef
        String txnRef = qs.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return VnPayUtil.ipnResponse("01", "Missing vnp_TxnRef");
        }
        var opt = paymentRepo.findByVnpTxnRef(txnRef);
        if (opt.isEmpty()) return VnPayUtil.ipnResponse("01", "Order not found");
        Payment p = opt.get();

        // Idempotent
        if (Boolean.TRUE.equals(p.getIpnVerified()) || "SUCCESS".equalsIgnoreCase(p.getStatus())) {
            return VnPayUtil.ipnResponse("00", "Order already confirmed");
        }

        // 3) Đối chiếu merchant / currency / amount
        String tmn = qs.get("vnp_TmnCode");
        if (tmn == null || !tmn.trim().equalsIgnoreCase(tmnCode.trim())) {
            return VnPayUtil.ipnResponse("97", "Invalid tmnCode");
        }
        if (!"VND".equalsIgnoreCase(qs.getOrDefault("vnp_CurrCode", "VND"))) {
            return VnPayUtil.ipnResponse("04", "Invalid currency");
        }

        long vnpAmount; // VNPay truyền VND x 100
        try {
            vnpAmount = Long.parseLong(qs.getOrDefault("vnp_Amount", "0"));
        } catch (NumberFormatException e) {
            return VnPayUtil.ipnResponse("04", "Invalid amount");
        }
        long expected = p.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
        if (vnpAmount != expected) {
            p.setStatus("FAILED");
            p.setVnpResponseCode("AMOUNT_MISMATCH");
            p.setUpdatedAt(LocalDateTime.now());
            paymentRepo.save(p);
            return VnPayUtil.ipnResponse("04", "Invalid amount");
        }

        // 4) Kết quả giao dịch
        String respCode   = qs.getOrDefault("vnp_ResponseCode", "");
        String tranStatus = qs.getOrDefault("vnp_TransactionStatus", "");
        boolean success   = "00".equals(respCode) && "00".equals(tranStatus);

        // Lưu info ngân hàng/thanh toán
        p.setVnpResponseCode(respCode);
        p.setVnpTransactionNo(qs.get("vnp_TransactionNo"));
        p.setVnpBankCode(qs.get("vnp_BankCode"));
        p.setVnpBankTranNo(qs.get("vnp_BankTranNo"));
        p.setVnpCardType(qs.get("vnp_CardType"));
        String payDate = qs.get("vnp_PayDate"); // yyyyMMddHHmmss
        if (payDate != null && payDate.matches("\\d{14}")) {
            var f = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            p.setPayTime(LocalDateTime.parse(payDate, f));
            p.setVnpPayDate(payDate);
        }
        p.setUpdatedAt(LocalDateTime.now());

        if (success) {
            p.setStatus("SUCCESS");
            p.setIpnVerified(true);
            paymentRepo.save(p);

            // 5) Hậu xử lý theo loại giao dịch
            if (txnRef.startsWith("F")) {
                Borrow b = p.getBorrow();
                if (b != null) {
                    // CỘNG DỒN số tiền đã trả
                    BigDecimal curPaid = (b.getFinePaidTotal() == null) ? BigDecimal.ZERO : b.getFinePaidTotal();
                    BigDecimal newPaid = curPaid.add(p.getAmount());
                    b.setFinePaidTotal(newPaid);

                    // Recalc trạng thái phạt theo quy tắc 24h tròn / freeDays hiện hành
                    borrowService.calculateOverdue(b);

                    // Nếu vừa đủ/đủ hơn, set thời điểm paid
                    if ("PAID".equalsIgnoreCase(b.getFineStatus())) {
                        b.setFinePaidAt((p.getPayTime() != null) ? p.getPayTime() : LocalDateTime.now());
                    }
                    borrowRepo.save(b);
                }
            } else if (txnRef.startsWith("M")) {
                int months = monthsFrom(p.getAmount()); // amount (VND) -> số tháng
                if (months < 1) months = 1;
                for (int i = 0; i < months; i++) {
                    membershipService.extendMonthly(p.getUser().getId());
                }
            }
            return VnPayUtil.ipnResponse("00", "Confirm Success");
        } else {
            p.setStatus("FAILED");
            paymentRepo.save(p);
            // vẫn trả 00 để VNPay ngừng retry – đã nhận và xử lý thất bại
            return VnPayUtil.ipnResponse("00", "Confirm Failed");
        }
    }

    // ============================== INTERNAL API ==============================
    /** Có thể dùng lại ở các luồng nội bộ khác (ngoài IPN). */
    @Transactional
    public void applyFinePaid(Long borrowId, BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.signum() <= 0) return;
        Borrow b = borrowRepo.findById(borrowId).orElseThrow();
        BigDecimal cur = (b.getFinePaidTotal() == null) ? BigDecimal.ZERO : b.getFinePaidTotal();
        b.setFinePaidTotal(cur.add(paidAmount));
        borrowService.calculateOverdue(b); // recalc đúng quy tắc 24h/freeDays
        borrowRepo.save(b);
    }
}
