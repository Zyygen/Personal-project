package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import com.example.demo.model.Category;

@Entity
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String author;

    private boolean available;
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private String imagePath;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    public String getDescription() {        // ✅ trả về String
        return description;
    }
    public void setDescription(String description) {  // ✅ nhận String
        this.description = description;
    }
    
    @Column(nullable = false)
    private int quantity; // Tổng số lượng hiện có trong thư viện

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // === Getter & Setter ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.available = quantity > 0; // Tự động cập nhật trạng thái available
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Giảm số lượng khi mượn
    public void decreaseQuantity(int amount) {
        this.quantity -= amount;
        if (this.quantity < 0) this.quantity = 0;
        this.available = this.quantity > 0;
    }

    // Tăng lại số lượng khi trả
    public void increaseQuantity(int amount) {
        this.quantity += amount;
        this.available = true;
    }
    

    // Tự động cập nhật updatedAt trước khi lưu mới hoặc cập nhật
    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}
