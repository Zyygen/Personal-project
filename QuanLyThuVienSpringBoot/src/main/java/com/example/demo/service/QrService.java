package com.example.demo.service;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Hashtable;
import javax.imageio.ImageIO;
import java.util.Base64;

@Service
public class QrService {
    public String toDataUriPng(String content, int size) {
        try {
            var matrix = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            var img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    img.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            var baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;   // <- QUAN TRỌNG
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
