package com.example.demo.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** VNPay 2.1.0 helper: build URL và verify IPN/Return một cách “kháng sai” */
public final class VnPayUtil {
    private VnPayUtil() {}

    /** Encode theo quy tắc VNPay: dùng URLEncoder, KHÔNG thay '+' -> '%20' */
    public static String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.US_ASCII.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String hmacSHA512(String secret, String data) {
        try {
            Mac h = Mac.getInstance("HmacSHA512");
            h.init(new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = h.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }

    /** Lọc, sort, encode & build query + secure hash (dùng khi TẠO URL thanh toán) */
    public static String buildPaymentUrl(String payUrl, Map<String,String> rawParams, String secret) {
        Map<String,String> params = canonicalize(rawParams);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query    = new StringBuilder();

        for (Iterator<Map.Entry<String,String>> it = params.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,String> e = it.next();
            String k = encode(e.getKey());
            String v = encode(e.getValue());
            hashData.append(k).append("=").append(v);
            query.append(k).append("=").append(v);
            if (it.hasNext()) { hashData.append("&"); query.append("&"); }
        }

        // (Tùy) kèm type để tiện debug; VNPay không yêu cầu nhưng chấp nhận
        query.append("&vnp_SecureHashType=HMACSHA512");

        String secureHash = hmacSHA512(secret, hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        // log debug
        System.out.println("[VNPAY][BUILD] hashData=" + hashData);
        System.out.println("[VNPAY][BUILD] secureHash=" + secureHash);

        return (payUrl.endsWith("?") ? payUrl : payUrl + "?") + query;
    }

    /** Verify “dual-mode”: thử CẢ 2 cách (encode & plain) để tránh sai khác do framework decode */
    public static boolean verifyFlexible(Map<String,String> allParams, String secret) {
        String recv = nv(allParams.get("vnp_SecureHash"));
        if (recv.isEmpty()) return false;

        String encoded = buildHashDataEncoded(allParams);
        String plain   = buildHashDataPlain(allParams);

        String h1 = hmacSHA512(secret, encoded);
        if (recv.equalsIgnoreCase(h1)) {
            System.out.println("[VNPAY][VERIFY] MATCH (encoded) hashData=" + encoded);
            return true;
        }

        String h2 = hmacSHA512(secret, plain);
        if (recv.equalsIgnoreCase(h2)) {
            System.out.println("[VNPAY][VERIFY] MATCH (plain) hashData=" + plain);
            return true;
        }

        System.out.println("[VNPAY][VERIFY] FAIL");
        System.out.println("[VNPAY][VERIFY] recv=" + recv);
        System.out.println("[VNPAY][VERIFY] encodedHash=" + h1);
        System.out.println("[VNPAY][VERIFY] plainHash  =" + h2);
        System.out.println("[VNPAY][VERIFY] encodedData=" + encoded);
        System.out.println("[VNPAY][VERIFY] plainData  =" + plain);

        return false;
    }

    // ===== helpers =====

    private static Map<String,String> canonicalize(Map<String,String> raw) {
        Map<String,String> m = new TreeMap<>();
        for (Map.Entry<String,String> e : raw.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            if (k.equalsIgnoreCase("vnp_SecureHash") || k.equalsIgnoreCase("vnp_SecureHashType")) continue;
            String v = e.getValue();
            if (v == null || v.trim().isEmpty()) continue;
            m.put(k, v.trim());
        }
        return m;
    }

    /** Dựng hashData với URL-encode từng phần tử (chuẩn giống lúc build URL) */
    private static String buildHashDataEncoded(Map<String,String> allParams) {
        Map<String,String> params = canonicalize(allParams);
        StringBuilder sb = new StringBuilder();
        for (Iterator<Map.Entry<String,String>> it = params.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,String> e = it.next();
            sb.append(encode(e.getKey())).append("=").append(encode(e.getValue()));
            if (it.hasNext()) sb.append("&");
        }
        return sb.toString();
    }

    /** Dựng hashData “plain” (không encode) – một số SDK mẫu IPN dùng cách này */
    private static String buildHashDataPlain(Map<String,String> allParams) {
        Map<String,String> params = canonicalize(allParams);
        StringBuilder sb = new StringBuilder();
        for (Iterator<Map.Entry<String,String>> it = params.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,String> e = it.next();
            sb.append(e.getKey()).append("=").append(nv(e.getValue()));
            if (it.hasNext()) sb.append("&");
        }
        return sb.toString();
    }

    private static String nv(String s) { return (s == null) ? "" : s; }

    /** Chuẩn JSON response cho IPN */
    public static String ipnResponse(String code, String msg) {
        return "{\"RspCode\":\"" + code + "\",\"Message\":\"" + msg + "\"}";
    }
}
