package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.repository.BookRepository;
import com.example.demo.service.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {

    private final BookRepository bookRepo;
    private final CategoryService categoryService;

    public SearchController(BookRepository bookRepo, CategoryService categoryService) {
        this.bookRepo = bookRepo;
        this.categoryService = categoryService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String q,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "6") int size,
                         Model model) {

        String keyword = (q == null) ? "" : q.trim();
        if (keyword.isEmpty()) {
            return "redirect:/home";
        }

        int pageIndex = Math.max(page - 1, 0);
        // luôn giới hạn 6 cuốn/trang
        int pageSize  = Math.min(Math.max(size, 1), 6);

        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Page<Book> result = bookRepo
                .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(keyword, keyword, pageable);

        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("books", result);
        model.addAttribute("q", keyword);

        // thông tin phân trang cho view
        model.addAttribute("showPagination", result.getTotalPages() > 1);
        model.addAttribute("currentPage", pageIndex + 1);
        model.addAttribute("totalPages", Math.max(result.getTotalPages(), 1));
        model.addAttribute("pageSize", pageSize);

        model.addAttribute("categoryId", null);
        model.addAttribute("notFound", result.getTotalElements() == 0);

        return "user/home";
    }

    @GetMapping("/search_ad")
    public String search_ad(@RequestParam(name = "q", required = false) String q,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "6") int size,
                            Model model) {

        String keyword = (q == null) ? "" : q.trim();
        if (keyword.isEmpty()) {
            return "redirect:/admin/home_ad";
        }

        int pageIndex = Math.max(page - 1, 0);
        // luôn giới hạn 6 cuốn/trang
        int pageSize  = Math.min(Math.max(size, 1), 6);

        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Page<Book> result = bookRepo
                .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(keyword, keyword, pageable);

        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("books", result);
        model.addAttribute("q", keyword);

        // thông tin phân trang cho view
        model.addAttribute("showPagination", result.getTotalPages() > 1);
        model.addAttribute("currentPage", pageIndex + 1);
        model.addAttribute("totalPages", Math.max(result.getTotalPages(), 1));
        model.addAttribute("pageSize", pageSize);

        model.addAttribute("categoryId", null);
        model.addAttribute("notFound", result.getTotalElements() == 0);

        return "admin/home_ad";
    }
}
