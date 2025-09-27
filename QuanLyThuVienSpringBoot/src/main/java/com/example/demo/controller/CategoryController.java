package com.example.demo.controller;

import com.example.demo.model.Category;
import com.example.demo.service.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String listCategories(Model model, @RequestParam(value = "message", required = false) String message) {
        model.addAttribute("categories", categoryService.findAll());
        if (message != null) model.addAttribute("message", message);
        return "category/list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("category", new Category());
        return "category/add";
    }

    @PostMapping("/add")
    public String addCategory(@ModelAttribute Category category, RedirectAttributes redirectAttributes) {
        categoryService.save(category);
        redirectAttributes.addAttribute("message", "Đã thêm danh mục thành công!");
        return "redirect:/admin/categories";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Category> category = categoryService.findById(id);
        if (category.isEmpty()) {
            redirectAttributes.addAttribute("message", "Không tìm thấy danh mục!");
            return "redirect:/admin/categories";
        }
        model.addAttribute("category", category.get());
        return "category/edit";
    }

    @PostMapping("/edit/{id}")
    public String editCategory(@PathVariable("id") Long id, @ModelAttribute Category category, RedirectAttributes redirectAttributes) {
        category.setId(id);
        categoryService.save(category);
        redirectAttributes.addAttribute("message", "Cập nhật danh mục thành công!");
        return "redirect:/admin/categories";
    }

    @GetMapping("/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteById(id);
            redirectAttributes.addAttribute("message", "Xóa danh mục thành công!");
        } catch (Exception e) {
            redirectAttributes.addAttribute("message", "Không thể xóa danh mục do đã có sách liên quan.");
        }
        return "redirect:/admin/categories";
    }
}
