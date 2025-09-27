package com.example.demo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;


@Entity
@Table(name = "payment")
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "borrow_id")
    private Borrow borrow;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider")      private String provider;     // "VNPAY"
    @Column(name = "method")        private String method;       // "VNPAY"
    @Column(name = "status")        private String status;       // PENDING/SUCCESS/FAILED
    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "currency")      private String currency;     // VND
    @Column(name = "order_info", length = 255)
    private String orderInfo;

    // mã tham chiếu nội bộ + gửi cho VNPay
    @Column(name = "vnp_bank_tran_no", length = 64)
    private String vnpBankTranNo;

    @Column(name = "vnp_card_type", length = 32)
    private String vnpCardType;
    @Column(name = "txn_ref", length = 64)
    private String txnRef;
    @Column(name = "vnp_txn_ref", length = 64, unique = true)
    private String vnpTxnRef;

    // các trường do VNPay trả
    @Column(name = "vnp_transaction_no", length = 64)
    private String vnpTransactionNo;
    @Column(name = "vnp_bank_code", length = 32)
    private String vnpBankCode;
    @Column(name = "vnp_response_code", length = 16)
    private String vnpResponseCode;
    @Column(name = "vnp_pay_date", length = 14)
    private String vnpPayDate;

    // thời điểm thanh toán đã parse
    @Column(name = "pay_time")
    private LocalDateTime payTime;

    @Column(name = "created_at")    private LocalDateTime createdAt;
    @Column(name = "updated_at")    private LocalDateTime updatedAt;
    @Column(name = "ipn_verified")  private Boolean ipnVerified;

    @Lob @Column(name = "raw_callback")
    private String rawCallback;

    // getters/setters
    public Long getId() { return id; }
    public Borrow getBorrow() { return borrow; }
    public void setBorrow(Borrow borrow) { this.borrow = borrow; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getOrderInfo() { return orderInfo; }
    public void setOrderInfo(String orderInfo) { this.orderInfo = orderInfo; }
    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }
    public String getVnpTxnRef() { return vnpTxnRef; }
    public void setVnpTxnRef(String vnpTxnRef) { this.vnpTxnRef = vnpTxnRef; }
    public String getVnpTransactionNo() { return vnpTransactionNo; }
    public void setVnpTransactionNo(String vnpTransactionNo) { this.vnpTransactionNo = vnpTransactionNo; }
    public String getVnpBankCode() { return vnpBankCode; }
    public void setVnpBankCode(String vnpBankCode) { this.vnpBankCode = vnpBankCode; }
    public String getVnpResponseCode() { return vnpResponseCode; }
    public void setVnpResponseCode(String vnpResponseCode) { this.vnpResponseCode = vnpResponseCode; }
    public String getVnpPayDate() { return vnpPayDate; }
    public void setVnpPayDate(String vnpPayDate) { this.vnpPayDate = vnpPayDate; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getIpnVerified() { return ipnVerified; }
    public void setIpnVerified(Boolean ipnVerified) { this.ipnVerified = ipnVerified; }
    public String getRawCallback() { return rawCallback; }
    public void setRawCallback(String rawCallback) { this.rawCallback = rawCallback; }
    public String getVnpBankTranNo() { return vnpBankTranNo; }
    public void setVnpBankTranNo(String vnpBankTranNo) { this.vnpBankTranNo = vnpBankTranNo; }

    public String getVnpCardType() { return vnpCardType; }
    public void setVnpCardType(String vnpCardType) { this.vnpCardType = vnpCardType; }
}
