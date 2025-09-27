package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.model.Category;
import com.example.demo.service.BookService;
import com.example.demo.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private BookService bookService;

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    /** HOME cho user/ẩn danh; admin sẽ redirect sang /admin/home_ad */
    @GetMapping("/home")
    public String home(Authentication authentication,
                       @RequestParam(name = "page", defaultValue = "1") int page, // UI 1-based
                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                       Model model) {

        // Nếu là admin -> chuyển sang trang admin để dùng layout & logic riêng
        boolean isAdmin = authentication != null &&
                authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            StringBuilder url = new StringBuilder("/admin/home_ad?page=").append(Math.max(page, 1));
            if (categoryId != null && categoryId > 0) url.append("&categoryId=").append(categoryId);
            return "redirect:" + url;
        }

        // --- Inputs & paging ---
        page = Math.max(page, 1);
        final int pageSize = 6;
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);

        // --- Danh mục ---
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);
        model.addAttribute("categoryId", categoryId);

        // --- Xác định category đang chọn (nếu có) ---
        Category selectedCategory = null;
        if (categoryId != null && categoryId != 0) {
            selectedCategory = categoryService.findById(categoryId).orElse(null);
        }

        // --- Trang dữ liệu chính (Page<Book>) ---
        Page<Book> books = (selectedCategory != null)
                ? bookService.findByCategory(selectedCategory, pageable)
                : bookService.getBooksPage(pageable);

        if (books == null) {
            books = new PageImpl<>(List.of(), pageable, 0);
        }

        // --- TOP 6 nổi bật ---
        Pageable top6 = PageRequest.of(0, 6, sort);
        List<Book> featuredTop6 = (selectedCategory != null)
                ? bookService.findByCategory(selectedCategory, top6).getContent()
                : bookService.getBooksPage(top6).getContent();
        model.addAttribute("featuredTop6", featuredTop6 == null ? List.of() : featuredTop6);

        // --- Phân trang cho view ---
        int totalPages = Math.max(books.getTotalPages(), 0);
        if (totalPages > 0 && page > totalPages) {
            String base = "/home?page=" + totalPages;
            if (categoryId != null && categoryId != 0) base += "&categoryId=" + categoryId;
            return "redirect:" + base;
        }

        model.addAttribute("books", books);              
        model.addAttribute("showPagination", totalPages > 1);
        model.addAttribute("currentPage", page);         
        model.addAttribute("totalPages", totalPages);

        return "user/home";
    }
}
