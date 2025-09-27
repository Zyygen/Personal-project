package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/books")
public class BookController {

    private final BookRepository bookRepo;

    public BookController(BookRepository bookRepo) {
        this.bookRepo = bookRepo;
    }

    /** Danh sách sách (công khai) — có tìm kiếm & phân trang cơ bản */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "12") int size,
                       @RequestParam(required = false) String q,
                       Model model) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 60));

        Page<Book> result;
        if (q == null || q.isBlank()) {
            result = bookRepo.findAll(pageable);
        } else {
            // tuỳ bạn có method gì trong repo: ví dụ findByTitleContainingIgnoreCase
            result = bookRepo.findByTitleContainingIgnoreCase(q.trim(), pageable);
        }

        model.addAttribute("page", result);
        model.addAttribute("q", q);
        return "user/home"; // trang listing như ảnh bạn gửi
    }

    /** Chi tiết sách (công khai) */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Book b = bookRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
        model.addAttribute("book", b);
        return "user/book_detail"; // giữ đúng tên template bạn đang dùng
    }

    /** Tuỳ chọn: URL có slug cho đẹp link (slug bị bỏ qua nếu sai) */
    @GetMapping("/{id}/{slug}")
    public String detailWithSlug(@PathVariable Long id, @PathVariable String slug, Model model) {
        return detail(id, model);
    }
}
