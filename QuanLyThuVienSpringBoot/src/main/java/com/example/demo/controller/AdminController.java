package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.model.Borrow;
import com.example.demo.model.Category;
import com.example.demo.service.BookService;
import com.example.demo.service.CategoryService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import com.example.demo.repository.BorrowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private BorrowRepository borrowRepository;
    private final BookService bookService;
    private final CategoryService categoryService;
    public AdminController(BookService bookService, CategoryService categoryService) {
        this.bookService = bookService;
        this.categoryService = categoryService;
    }

    @PersistenceContext
    private EntityManager em;

    /** Trang HOME dành cho ADMIN: 6 cuốn/trang, lọc theo category & q */
    @GetMapping("/home_ad")
    public String adminHome(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "6") int size,
                            @RequestParam(required = false) Long categoryId,
                            @RequestParam(required = false) String q,
                            Model model) {

        int p = Math.max(page, 1) - 1;   // UI -> 0-based
        size = Math.max(size, 1);

        // Điều kiện động
        StringBuilder where = new StringBuilder(" where 1=1 ");
        Map<String, Object> params = new HashMap<>();

        if (categoryId != null && categoryId > 0) {
            // Nếu Book có quan hệ Category -> dùng bk.category.id
            // Nếu Book có cột categoryId kiểu số -> đổi thành "bk.categoryId = :cat"
            where.append(" and bk.category.id = :cat ");
            params.put("cat", categoryId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and lower(bk.title) like :q ");
            params.put("q", "%" + q.toLowerCase().trim() + "%");
        }

        // COUNT
        String cntSql = "select count(bk) from Book bk" + where;
        TypedQuery<Long> cntQ = em.createQuery(cntSql, Long.class);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            cntQ.setParameter(e.getKey(), e.getValue());
        }
        long total = cntQ.getSingleResult();

        // PAGE CONTENT
        String selSql = "select bk from Book bk" + where + " order by bk.id desc";
        TypedQuery<Book> selQ = em.createQuery(selSql, Book.class);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            selQ.setParameter(e.getKey(), e.getValue());
        }
        List<Book> content = selQ.setFirstResult(p * size).setMaxResults(size).getResultList();

        Page<Book> books = new PageImpl<>(content, PageRequest.of(p, size), total);

        // Biến cho view admin
        model.addAttribute("books", books);                          // view: ${books.content}
        model.addAttribute("showPagination", books.getTotalPages() > 1);
        model.addAttribute("currentPage", p + 1);                    // 1-based
        model.addAttribute("totalPages", Math.max(books.getTotalPages(), 1));
        model.addAttribute("categoryId", categoryId);
        // ô tìm kiếm đọc ${param.q} trực tiếp từ request
        // --- Phân trang cho view ---
        int totalPages = Math.max(books.getTotalPages(), 0);
        if (totalPages > 0 && page > totalPages) {
            String base = "/admin/home_ad?page=" + totalPages;
            if (categoryId != null && categoryId != 0) base += "&categoryId=" + categoryId;
            return "redirect:" + base;
        }

        // (Nếu home_ad.html hiển thị danh sách danh mục ở sidebar/filter)
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);

        return "admin/home_ad"; 
    }
}
