package com.example.demo.repository;

import com.example.demo.model.Borrow;
import com.example.demo.model.User;
import com.example.demo.dto.TopBorrowerDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BorrowRepository extends JpaRepository<Borrow, Long> {
	
	// Có lịch sử bất kỳ
    boolean existsByBook_Id(Long bookId);

    // Đang mượn (chưa trả) — dùng đúng field returnDate
    boolean existsByBook_IdAndReturnDateIsNull(Long bookId);

    // (Tùy chọn) Alias nếu nơi khác gọi theo tên bookId
    @Query("select count(b) > 0 from Borrow b where b.book.id = :bookId and b.returnDate is null")
    boolean existsByBookIdAndReturnDateIsNull(@Param("bookId") Long bookId);
	    
    // Đếm theo nested property (giữ nguyên nếu nơi khác còn gọi)
    long countByUser_IdAndReturnDateIsNull(Long userId);
    
    long countByUserId(Long userId);
    
    // Tổng số bản sách đang mượn (nếu entity Borrow có field amount)
    @Query("select coalesce(sum(b.amount),0) from Borrow b where b.user.id = :uid and b.returnDate is null")
    Integer sumOpenAmountByUser(@Param("uid") Long userId);

    // Tổng số record Borrow đang mở (fallback nếu bạn muốn tính theo số record)
    @Query("select count(b) from Borrow b where b.user.id = :uid and b.returnDate is null")
    Long countOpenByUser(@Param("uid") Long userId);
    
    @Query("select count(b) from Borrow b where b.book.id = :bookId and b.returnDate is null")
    long countOpenByBookId(@Param("bookId") Long bookId);

    // ✅ Bản đếm theo tên “userId” nhưng dùng JPQL b.user.id để không phụ thuộc field userId trong entity
    @Query("select count(b) from Borrow b where b.user.id = :userId and b.returnDate is null")
    int countByUserIdAndReturnDateIsNull(@Param("userId") Long userId);

    // ===== Đang mượn (JOIN book) =====
    @EntityGraph(attributePaths = {"book"})
    List<Borrow> findByUserIdAndReturnDateIsNullOrderByBorrowDateDesc(Long userId);

    @EntityGraph(attributePaths = {"book"})
    List<Borrow> findByUserIdAndReturnDateIsNull(Long userId);

    @EntityGraph(attributePaths = {"book"})
    List<Borrow> findByUserAndReturnDateIsNull(User user);

    // ===== Đã trả (JOIN book) =====
    @EntityGraph(attributePaths = {"book"})
    List<Borrow> findByUserAndReturnDateIsNotNull(User user);

    @EntityGraph(attributePaths = {"book"})
    List<Borrow> findByUserIdAndReturnDateIsNotNullOrderByReturnDateDesc(Long userId);

    // ===== Tất cả chưa trả (JOIN book) =====
    @EntityGraph(attributePaths = {"book"})
    List<Borrow> findByReturnDateIsNull();

    // ===== Quá hạn (JOIN book) =====
    @EntityGraph(attributePaths = {"book"})
    List<Borrow> findByReturnDateIsNullAndDueDateBefore(LocalDateTime now);

    List<Borrow> findByDueDateBeforeAndReturnDateIsNull(LocalDateTime now);

    // ===== Thống kê =====
    long countByBorrowDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    boolean existsByUserIdAndReturnDateIsNullAndDueDateBefore(Long userId, LocalDateTime time);

    @Query("SELECT new com.example.demo.dto.TopBorrowerDTO(b.user.username, COUNT(b)) " +
           "FROM Borrow b GROUP BY b.user.username ORDER BY COUNT(b) DESC")
    List<TopBorrowerDTO> findTopBorrower(Pageable pageable);

    @Query("SELECT SUM(b.amount) FROM Borrow b WHERE b.returnDate IS NULL")
    Long getTotalBorrowedAmount();

    // Tổng số CUỐN đang mượn (chìa khóa để chặn vượt limit)
    @Query("select coalesce(sum(b.amount), 0) from Borrow b where b.user.id = :userId and b.returnDate is null")
    int sumActiveAmount(@Param("userId") Long userId);

    // Phân trang danh sách phiếu mượn chưa trả (JOIN book để render an toàn)
    @EntityGraph(attributePaths = {"book"})
    Page<Borrow> findByReturnDateIsNull(Pageable pageable);

    // Ràng buộc, dọn dữ liệu
    boolean existsByBookId(Long bookId);
    void deleteAllByUserId(Long userId);

    // ===== Merge theo hạn trả (tách dòng theo dueDate) =====
    Optional<Borrow> findFirstByUserIdAndBookIdAndReturnDateIsNullAndDueDate(
            Long userId, Long bookId, LocalDateTime dueDate
    );
    @Query("""
           select new com.example.demo.dto.TopBorrowerDTO(b.user.username, count(b))
           from Borrow b
           group by b.user.username
           order by count(b) desc
           """)
    List<TopBorrowerDTO> findTopBorrowers(Pageable pageable);

    // ===== Dùng riêng cho trang mượn: lấy đủ bản ghi, nạp sẵn book, sắp xếp theo hạn trả =====
    @Query("""
        select b from Borrow b
          join fetch b.book
         where b.user.id = :uid
           and b.returnDate is null
      order by b.dueDate asc, b.borrowDate desc, b.id desc
    """)
    List<Borrow> findOpenByUserFetchBookOrderByDue(@Param("uid") Long uid);
}
