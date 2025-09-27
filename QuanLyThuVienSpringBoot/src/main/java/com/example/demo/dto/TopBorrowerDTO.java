package com.example.demo.dto;

public class TopBorrowerDTO {

    private Long userId;     // có thể null nếu dùng ctor 2 tham số
    private String username;
    private long total;      // tổng lượt mượn hoặc tổng amount (tuỳ bạn đếm gì)

    public TopBorrowerDTO() {
    }

    // Dùng cho truy vấn đếm theo username (repo findTopBorrowers đếm COUNT(b))
    public TopBorrowerDTO(String username, long total) {
        this.username = username;
        this.total = total;
    }

    // Dùng cho truy vấn kèm userId + SUM(amount) trong StatisticsController
    public TopBorrowerDTO(Long userId, String username, Long total) {
        this.userId = userId;
        this.username = username;
        this.total = (total == null ? 0L : total);
    }

    // --- getters/setters ---
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public long getTotal() {
        return total;
    }
    public void setTotal(long total) {
        this.total = total;
    }

    @Override
    public String toString() {
        return "TopBorrowerDTO{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", total=" + total +
                '}';
    }
}
