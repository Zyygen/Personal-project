package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadService {

    // Thư mục gốc của "uploads" (cha của thư mục "img")
    @Value("${app.upload-dir}")
    private String uploadRoot; // ví dụ: C:/Users/ASUS/.../uploads

    /** Lưu ảnh bìa sách vào uploads/img và trả về đường dẫn WEB: /uploads/img/<file> */
    public String saveBookImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;

        String ext = Optional.ofNullable(file.getOriginalFilename())
                .map(n -> n.contains(".") ? n.substring(n.lastIndexOf('.')) : "")
                .orElse("")
                .toLowerCase();

        String filename = UUID.randomUUID() + ext;

        Path imgDir = Paths.get(uploadRoot, "img").toAbsolutePath().normalize();
        Files.createDirectories(imgDir);

        Path dest = imgDir.resolve(filename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        return "/uploads/img/" + filename; // dùng cho Thymeleaf & lưu DB
    }

    /** (tuỳ chọn) Xoá file theo đường dẫn WEB /uploads/... */
    public void deleteByWebPath(String webPath) {
        if (webPath == null || !webPath.startsWith("/uploads/")) return;
        String relative = webPath.replaceFirst("^/uploads/", "");
        Path real = Paths.get(uploadRoot).resolve(relative).normalize();
        try { Files.deleteIfExists(real); } catch (IOException ignored) {}
    }
}
