package com.example.demo.service;

import com.example.demo.dto.TopBorrowerDTO;
import com.example.demo.model.Book;
import com.example.demo.model.Borrow;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class StatisticsService {

    @Autowired
    private BorrowRepository borrowRepository;

    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public long getTodayBorrowCount() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.atTime(23, 59, 59);
        return borrowRepository.countByBorrowDateBetween(startOfDay, endOfDay);
    }

    public long getWeekBorrowCount() {
        LocalDate today   = LocalDate.now();
        LocalDate monday  = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate sunday  = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
        LocalDateTime startOfWeek = monday.atStartOfDay();
        LocalDateTime endOfWeek   = sunday.atTime(23, 59, 59);
        return borrowRepository.countByBorrowDateBetween(startOfWeek, endOfWeek);
    }

    public long getMonthBorrowCount() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth   = today.withDayOfMonth(today.lengthOfMonth()).atTime(23, 59, 59);
        return borrowRepository.countByBorrowDateBetween(startOfMonth, endOfMonth);
    }

    public List<Borrow> getOverdueBorrows() {
        return borrowRepository.findByDueDateBeforeAndReturnDateIsNull(LocalDateTime.now());
    }

    public TopBorrowerDTO getTopBorrower() {
        String jpql =
            "SELECT u.username, COUNT(b.id) " +
            "FROM Borrow b JOIN b.user u " +
            "GROUP BY u.username ORDER BY COUNT(b.id) DESC";
        Query query = entityManager.createQuery(jpql).setMaxResults(1);
        List<Object[]> result = query.getResultList();
        if (!result.isEmpty()) {
            Object[] row = result.get(0);
            return new TopBorrowerDTO((String) row[0], ((Long) row[1]).intValue());
        }
        return null;
    }

    public Page<Book> getAvailableBooks(Pageable pageable) {
        return bookRepository.findByQuantityGreaterThan(0, pageable);
    }

    public List<Book> getAvailableBooks() {
        return bookRepository.findBooksInStock();
    }

    public List<Borrow> getBorrowedBooks() {
        return borrowRepository.findByReturnDateIsNull();
    }

    public long getTotalAvailableQuantity() {
        return bookRepository.findAll().stream()
                .mapToLong(Book::getQuantity)
                .sum();
    }

    public long getTotalBorrowedQuantity() {
        return borrowRepository.findByReturnDateIsNull().stream()
                .mapToLong(Borrow::getAmount)
                .sum();
    }

    public Page<Borrow> getCurrentBorrows(Pageable pageable) {
        return borrowRepository.findByReturnDateIsNull(pageable);
    }
}

