package com.example.demo.controller;

import com.example.demo.dto.TopBorrowerDTO;
import com.example.demo.model.Book;
import com.example.demo.model.Borrow;
import com.example.demo.model.Category;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/admin/statistics")
public class StatisticsController {

    private static final int FINE_PER_DAY = 5000;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/status")
    public String status(@RequestParam(value = "categoryId", required = false) Long categoryId,
                         @RequestParam(value = "q", required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size,
                         Model model) {

        Long cat = (categoryId != null && categoryId == 0L) ? null : categoryId;
        String keyword = (q == null) ? null : q.trim();
        if (keyword != null && keyword.isEmpty()) keyword = null;
        int pageIndex = Math.max(page, 0);
        int pageSize  = Math.min(Math.max(size, 1), 60);

        List<Borrow> borrowed = em.createQuery("""
                select distinct b
                from Borrow b
                left join fetch b.user u
                left join fetch b.book bk
                where b.returnDate is null
                order by b.dueDate asc
                """, Borrow.class).getResultList();
        borrowed.sort(Comparator.comparing(Borrow::getDueDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        model.addAttribute("borrowedBooks", borrowed);
        Long totalBorrowedQty = em.createQuery(
                "select coalesce(sum(b.amount), 0) from Borrow b where b.returnDate is null",
                Long.class
        ).getSingleResult();
        model.addAttribute("totalBorrowedQty", totalBorrowedQty);
        model.addAttribute("totalBorrowedBooks", totalBorrowedQty);

        Page<Book> bookPage = pageBooksFiltered(cat, keyword, pageIndex, pageSize);
        Long totalAvailableQuantity = sumQuantityFiltered(cat, keyword);

        model.addAttribute("bookPage", bookPage);
        model.addAttribute("totalAvailableQuantity", totalAvailableQuantity == null ? 0 : totalAvailableQuantity);

        List<Category> categories = em.createQuery("""
                select c from Category c order by c.name asc
                """, Category.class).getResultList();
        model.addAttribute("categories", categories);
        model.addAttribute("categoryId", cat);
        model.addAttribute("q", keyword);

        return "admin/statistics/status";
    }
    @GetMapping("/report")
    public String report(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size,
                         Model model) {

        LocalDate today = LocalDate.now();
        long todayCount = countByBorrowDateBetween(today.atStartOfDay(), today.atTime(23,59,59));

        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        long weekCount = countByBorrowDateBetween(monday.atStartOfDay(), sunday.atTime(23,59,59));

        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = today.withDayOfMonth(today.lengthOfMonth());
        long monthCount = countByBorrowDateBetween(firstDay.atStartOfDay(), lastDay.atTime(23,59,59));

        model.addAttribute("todayBorrowCount", todayCount);
        model.addAttribute("weekBorrowCount",  weekCount);
        model.addAttribute("monthBorrowCount", monthCount);
        
        LocalDateTime now = LocalDateTime.now();
        List<Borrow> overdueList = em.createQuery("""
                select distinct b
                from Borrow b
                left join fetch b.user u
                left join fetch b.book bk
                where b.returnDate is null and b.dueDate < :now
                order by b.dueDate asc
                """, Borrow.class)
                .setParameter("now", now)
                .getResultList();
        for (Borrow b : overdueList) {
            int days = 0;
            if (b.getDueDate() != null) {
                days = (int) Math.max(0,
                        ChronoUnit.DAYS.between(b.getDueDate().toLocalDate(), LocalDate.now()));
            }
            BigDecimal fine = (days > 0) ? BigDecimal.valueOf((long) days * FINE_PER_DAY) : BigDecimal.ZERO;
            trySet(b, "setOverdueDays", int.class, days);
            trySet(b, "setFineAmount",  BigDecimal.class, fine);
        }
        overdueList.sort(Comparator.comparing(Borrow::getDueDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        model.addAttribute("overdueList", overdueList);
        List<Object[]> topRows = em.createQuery("""
                select u.username, count(b)
                from Borrow b join b.user u
                group by u.username
                order by count(b) desc
                """, Object[].class)
                .setMaxResults(1)
                .getResultList();

        TopBorrowerDTO topBorrower = null;
        if (!topRows.isEmpty()) {
            Object[] r = topRows.get(0);
            String username = (String) r[0];
            long cnt = (Long) r[1];
            topBorrower = new TopBorrowerDTO(username, (int) cnt);
        }
        model.addAttribute("topBorrower", topBorrower);
        Page<Borrow> borrowersPage = pageCurrentWithUserBook(page, size);
        model.addAttribute("borrowersPage", borrowersPage);

        return "admin/statistics/report";
    }
    private long countByBorrowDateBetween(LocalDateTime start, LocalDateTime end) {
        return em.createQuery("""
                select count(b) from Borrow b
                where b.borrowDate between :s and :e
                """, Long.class)
                .setParameter("s", start)
                .setParameter("e", end)
                .getSingleResult();
    }

    private Page<Book> pageBooksFiltered(Long categoryId, String q, int page, int size) {
        Long total = em.createQuery("""
                select count(bk)
                from Book bk
                where (:categoryId is null or bk.category.id = :categoryId)
                  and (
                       :q is null
                       or lower(bk.title)  like lower(concat('%', :q, '%'))
                       or lower(bk.author) like lower(concat('%', :q, '%'))
                  )
                """, Long.class)
                .setParameter("categoryId", categoryId)
                .setParameter("q", q)
                .getSingleResult();

        if (total == 0) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        }
        TypedQuery<Book> tq = em.createQuery("""
                select bk
                from Book bk
                left join fetch bk.category c
                where (:categoryId is null or bk.category.id = :categoryId)
                  and (
                       :q is null
                       or lower(bk.title)  like lower(concat('%', :q, '%'))
                       or lower(bk.author) like lower(concat('%', :q, '%'))
                  )
                order by bk.title asc
                """, Book.class);
        tq.setParameter("categoryId", categoryId);
        tq.setParameter("q", q);
        List<Book> content = tq
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }
    private Long sumQuantityFiltered(Long categoryId, String q) {
        return em.createQuery("""
                select coalesce(sum(bk.quantity), 0)
                from Book bk
                where (:categoryId is null or bk.category.id = :categoryId)
                  and (
                       :q is null
                       or lower(bk.title)  like lower(concat('%', :q, '%'))
                       or lower(bk.author) like lower(concat('%', :q, '%'))
                  )
                """, Long.class)
                .setParameter("categoryId", categoryId)
                .setParameter("q", q)
                .getSingleResult();
    }
    private Page<Borrow> pageCurrentWithUserBook(int page, int size) {
        List<Long> ids = em.createQuery("""
                select b.id
                from Borrow b
                where b.returnDate is null
                order by b.id desc
                """, Long.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        if (ids.isEmpty()) {
            long total = countCurrent();
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), total);
        }
        TypedQuery<Borrow> q = em.createQuery("""
                select distinct b
                from Borrow b
                left join fetch b.user u
                left join fetch b.book bk
                where b.id in :ids
                order by b.id desc
                """, Borrow.class);
        q.setParameter("ids", ids);
        List<Borrow> content = q.getResultList();

        long total = countCurrent();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    private long countCurrent() {
        return em.createQuery("""
                select count(b)
                from Borrow b
                where b.returnDate is null
                """, Long.class).getSingleResult();
    }
    private void trySet(Object target, String setter, Class<?> paramType, Object value) {
        try {
            Method m = target.getClass().getMethod(setter, paramType);
            m.invoke(target, value);
            return;
        } catch (Exception ignore) { }
        try {
            if (paramType == int.class) {
                Method m = target.getClass().getMethod(setter, Integer.class);
                m.invoke(target, Integer.valueOf((Integer) value));
                return;
            }
            if (paramType == Integer.class) {
                Method m = target.getClass().getMethod(setter, int.class);
                m.invoke(target, ((Integer) value).intValue());
                return;
            }
        } catch (Exception ignore) { }
    }
}
