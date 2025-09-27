package com.example.demo.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@Controller
public class QrImageController {

    @GetMapping("/qr.png")
    public void qr(@RequestParam("c") String content,
                   @RequestParam(value = "s", defaultValue = "220") int size,
                   HttpServletResponse resp) throws Exception {
        resp.setContentType("image/png");
        BitMatrix matrix = new MultiFormatWriter().encode(
                new String(content.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
                BarcodeFormat.QR_CODE, size, size);
        try (OutputStream os = resp.getOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", os);
        }
    }
}
