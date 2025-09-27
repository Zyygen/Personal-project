package com.example.demo.repository;

import com.example.demo.model.Book;
import com.example.demo.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    Page<Book> findByCategory_Id(Long categoryId, Pageable pageable);

    List<Book> findByAvailable(boolean available);

    Page<Book> findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCase(
            String title, String author, Pageable pageable);

    // Không phân trang
    List<Book> findByTitleContainingIgnoreCase(String keyword);
    List<Book> findByAuthorContainingIgnoreCase(String author);
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author);

    // Có phân trang
    Page<Book> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
    Page<Book> findByAuthorContainingIgnoreCase(String author, Pageable pageable);
    Page<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author, Pageable pageable);

    // Còn trong kho
    @Query("SELECT b FROM Book b WHERE b.quantity > 0")
    List<Book> findBooksInStock();

    Page<Book> findByQuantityGreaterThan(int quantity, Pageable pageable);

    List<Book> findByCategory(Category category);
    Page<Book> findByCategory(Category category, Pageable pageable);

    // Lọc phục vụ trang delete (keyword/author có thể null hoặc rỗng)
    @Query("""
           SELECT b FROM Book b
           WHERE (:keyword IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
             AND (:author  IS NULL OR LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%')))
             AND (:categoryId IS NULL OR b.category.id = :categoryId)
           """)
    Page<Book> searchBooksForDelete(@Param("keyword") String keyword,
                                    @Param("author") String author,
                                    @Param("categoryId") Long categoryId,
                                    Pageable pageable);
}
