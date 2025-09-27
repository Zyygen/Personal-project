package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.model.Category;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.service.UploadService;
import com.example.demo.repository.BorrowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Controller
@RequestMapping("/admin/books")
public class AdminBookController {

    private final BookRepository bookRepo;
    private final CategoryRepository categoryRepo;
    private final BorrowRepository borrowRepo;
    private final UploadService uploadService;

    // (Lưu ý dưới cùng về upload dir)
    private static final Path STATIC_UPLOAD_DIR = Paths.get("src/main/resources/static/uploads");

    public AdminBookController(BookRepository bookRepo, CategoryRepository categoryRepo, BorrowRepository borrowRepo, UploadService uploadService) {
        this.bookRepo = bookRepo;
        this.categoryRepo = categoryRepo;
        this.borrowRepo = borrowRepo;
        this.uploadService = uploadService;
    }

    @GetMapping("/add")
    public String addForm(@RequestParam(value = "prefill", required = false) Long prefillId,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "author", required = false) String author,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          Model model) {

        Book book = (prefillId != null)
                ? bookRepo.findById(prefillId).orElse(new Book())
                : new Book();

        if (!model.containsAttribute("book")) {
            model.addAttribute("book", book);
        }
        model.addAttribute("categories", categoryRepo.findAll());

        boolean hasQuery = (keyword != null && !keyword.isBlank()) || (author != null && !author.isBlank());
        if (hasQuery) {
            Pageable pageable = PageRequest.of(Math.max(page, 0), 10);
            Page<Book> books = bookRepo.findAll(pageable);
            model.addAttribute("books", books);
        }

        return "admin/books/add_book";
    }

    @PostMapping("/add")
    public String save(@ModelAttribute("book") Book book,
                       @RequestParam(value = "image", required = false) MultipartFile image,
                       RedirectAttributes ra) throws IOException {

        // bind category an toàn
        if (book.getCategory() != null && book.getCategory().getId() != null) {
            categoryRepo.findById(book.getCategory().getId()).ifPresent(book::setCategory);
        } else {
            book.setCategory(null);
        }

        // Lưu ảnh (nếu có) -> nhận về đường dẫn WEB /uploads/img/...
        if (image != null && !image.isEmpty()) {
            String webPath = uploadService.saveBookImage(image);
            book.setImagePath(webPath);
        }

        bookRepo.save(book);
        ra.addFlashAttribute("message", "Đã thêm sách mới thành công.");
        return "redirect:/admin/books/add";
    }

    @GetMapping("/edit")
    public String editPage(@RequestParam(value = "page", defaultValue = "0") int page,
                           @RequestParam(value = "editId", required = false) Long editId,
                           Model model) {

        page = Math.max(page, 0);
        Pageable pageable = PageRequest.of(page, 6);
        Page<Book> pageBooks = bookRepo.findAll(pageable);

        model.addAttribute("books", pageBooks.getContent());
        model.addAttribute("totalPages", pageBooks.getTotalPages());
        model.addAttribute("page", page);

        if (editId != null) {
            bookRepo.findById(editId).ifPresent(b -> model.addAttribute("book", b));
        } else if (!model.containsAttribute("book")) {
            model.addAttribute("book", new Book());
        }
        model.addAttribute("categories", categoryRepo.findAll());

        return "admin/books/edit_book";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute("book") Book form,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         RedirectAttributes ra) throws IOException {

        Book existing = bookRepo.findById(form.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sách có id = " + form.getId()));

        existing.setTitle(form.getTitle());
        existing.setAuthor(form.getAuthor());
        existing.setQuantity(form.getQuantity());
        existing.setDescription(form.getDescription());

        if (form.getCategory() != null && form.getCategory().getId() != null) {
            categoryRepo.findById(form.getCategory().getId()).ifPresent(existing::setCategory);
        } else {
            existing.setCategory(null);
        }

        if (image != null && !image.isEmpty()) {
            // (tuỳ chọn) xoá ảnh cũ:
            // uploadService.deleteByWebPath(existing.getImagePath());
            String webPath = uploadService.saveBookImage(image);
            existing.setImagePath(webPath);
        }

        bookRepo.save(existing);
        ra.addFlashAttribute("message", "Đã cập nhật sách.");
        return "redirect:/admin/books/edit?editId=" + existing.getId();
    }

    @GetMapping("/delete")
    public String listForDelete(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String author,
                                @RequestParam(required = false) Long categoryId,
                                Model model) {

        Pageable pageable = PageRequest.of(Math.max(page,0), 10, Sort.by("id").descending());

        // rỗng -> null để query gọn
        String k = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String a = (author  == null || author.isBlank())  ? null : author.trim();

        Page<Book> books = bookRepo.searchBooksForDelete(k, a, categoryId, pageable);

        model.addAttribute("books", books);
        model.addAttribute("categories", categoryRepo.findAll());
        return "admin/books/delete_book";
    }

    @PostMapping("/delete/{id}")
    public String doDelete(@PathVariable Long id,
                           @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String author,
                           @RequestParam(required = false) Long categoryId,
                           RedirectAttributes ra) {

        // Chặn xoá khi đang mượn hoặc đã có lịch sử mượn
    	boolean beingBorrowed = borrowRepo.existsByBook_IdAndReturnDateIsNull(id);
        boolean hasHistory    = borrowRepo.existsByBookId(id);
        if (beingBorrowed || hasHistory) {
            ra.addFlashAttribute("errorCode", "cannotDeleteHasBorrow");
        } else if (bookRepo.existsById(id)) {
            bookRepo.deleteById(id);
            ra.addFlashAttribute("message", "Đã xoá sách ID=" + id);
        } else {
            ra.addFlashAttribute("error", "Sách không tồn tại.");
        }

        // redirect về lại đúng trang + bộ lọc cũ
        String url = UriComponentsBuilder.fromPath("/admin/books/delete")
                .queryParam("page", page)
                .queryParam("keyword", keyword)
                .queryParam("author", author)
                .queryParam("categoryId", categoryId)
                .build().toUriString();
        return "redirect:" + url;
    }

    private void handleUploadAndSetImagePath(MultipartFile image, Book target) throws IOException {
        if (image == null || image.isEmpty()) return;

        Files.createDirectories(STATIC_UPLOAD_DIR);

        String original = image.getOriginalFilename();
        String ext = (StringUtils.hasText(original) && original.contains("."))
                ? original.substring(original.lastIndexOf("."))
                : "";
        String filename = UUID.randomUUID() + ext;

        Path dest = STATIC_UPLOAD_DIR.resolve(filename);
        Files.copy(image.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        target.setImagePath("/uploads/" + filename); // URL client sẽ tải
    }
}
