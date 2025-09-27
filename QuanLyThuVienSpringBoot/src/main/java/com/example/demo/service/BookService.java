package com.example.demo.service;

import com.example.demo.model.Book;
import com.example.demo.model.Category;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BorrowRepository borrowRepository;
    public Page<Book> findByCategoryId(Long categoryId, Pageable pageable) {
        return bookRepository.findByCategory_Id(categoryId, pageable);
    }

    public BookService(BookRepository bookRepository, BorrowRepository borrowRepository) {
        this.bookRepository = bookRepository;
        this.borrowRepository = borrowRepository;
    }

    /** Dùng cho controller chi tiết sách (trả về Optional) */
    public Optional<Book> findById(Long id) {
        return bookRepository.findById(id);
    }

    /** Giữ nguyên API cũ nếu nơi khác đang dùng */
    public Book getBookById(Long id) {
        return bookRepository.findById(id).orElse(null);
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Page<Book> getAllBooks(Pageable pageable) {
        return bookRepository.findAll(pageable);
    }

 // Tìm theo 1 keyword (tiêu đề HOẶC tác giả)
    public Page<Book> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable); // hoặc bookRepository.findAll(pageable)
        }
        String kw = keyword.trim();
        return bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(kw, kw, pageable);
    }

    // Tìm theo tiêu đề VÀ/HOẶC tác giả (2 ô tìm riêng)
    public Page<Book> search(String title, String author, Pageable p) {
        boolean hasTitle  = title  != null && !title.isBlank();
        boolean hasAuthor = author != null && !author.isBlank();

        if (hasTitle && hasAuthor) {
            return bookRepository
                    .findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCase(title.trim(), author.trim(), p);
        } else if (hasTitle) {
            return bookRepository.findByTitleContainingIgnoreCase(title.trim(), p);
        } else if (hasAuthor) {
            return bookRepository.findByAuthorContainingIgnoreCase(author.trim(), p);
        } else {
            return Page.empty(p);
        }
    }
    public List<Book> searchByTitle(String keyword) {
        return bookRepository.findByTitleContainingIgnoreCase(keyword);
    }

    public Page<Book> searchByTitle(String keyword, Pageable pageable) {
        return bookRepository.findByTitleContainingIgnoreCase(keyword, pageable);
    }

    public List<Book> searchByAuthor(String author) {
        return bookRepository.findByAuthorContainingIgnoreCase(author);
    }

    public Page<Book> searchByAuthor(String author, Pageable pageable) {
        return bookRepository.findByAuthorContainingIgnoreCase(author, pageable);
    }

    /** Lưu/ cập nhật: tự set available theo quantity */
    public void saveBook(Book book) {
        book.setAvailable(book.getQuantity() > 0);
        bookRepository.save(book);
    }

    public boolean updateBook(Book updatedBook) {
        Optional<Book> existingBook = bookRepository.findById(updatedBook.getId());
        if (existingBook.isPresent()) {
            saveBook(updatedBook);
            return true;
        }
        return false;
    }

    /** Xoá: chặn khi đã có phiếu mượn sử dụng sách, không làm trang trắng */
    public boolean deleteBook(Long id) {
        try {
            if (borrowRepository.existsByBookId(id)) {
                return false;
            }
            bookRepository.deleteById(id);
            return true;
        } catch (EmptyResultDataAccessException ex) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Phân trang cho admin/home */
    public Page<Book> getBooksPage(Pageable pageable) {
        return bookRepository.findAll(pageable);
    }

    /** Lọc theo danh mục (không phân trang) */
    public List<Book> findByCategory(Category category) {
        return bookRepository.findByCategory(category);
    }

    /** Lọc theo danh mục (có phân trang) */
    public Page<Book> findByCategory(Category category, Pageable pageable) {
        return bookRepository.findByCategory(category, pageable);
    }

    /** Tìm kiếm khi xoá (giữ nguyên logic bạn đã có) */
    public Page<Book> searchBooksForDelete(String keyword, String author, Long categoryId, Pageable pageable) {
        if ((keyword == null || keyword.isEmpty()) &&
            (author == null || author.isEmpty()) &&
            categoryId == null) {
            return bookRepository.findAll(pageable);
        }
        return bookRepository.searchBooksForDelete(keyword, author, categoryId, pageable);
    }
}
