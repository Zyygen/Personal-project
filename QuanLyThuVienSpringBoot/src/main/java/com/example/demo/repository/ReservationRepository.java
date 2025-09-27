package com.example.demo.repository;

import com.example.demo.model.Reservation;
import com.example.demo.model.Reservation.Status;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // ===== Các truy vấn theo user/status =====
    List<Reservation> findAllByUser_IdAndStatusIn(Long userId, Collection<Status> statuses);

    Optional<Reservation> findByUser_IdAndBook_IdAndStatusIn(Long userId, Long bookId, Collection<Status> statuses);

    boolean existsByUser_IdAndBook_IdAndStatusIn(Long userId, Long bookId, Collection<Status> statuses);

    // ===== Theo book/status =====
    List<Reservation> findByBook_IdAndStatusIn(Long bookId, Collection<Status> statuses);

    long countByBook_IdAndStatusIn(Long bookId, Collection<Status> statuses);

    Optional<Reservation> findFirstByBook_IdAndStatusInOrderByCreatedAtAsc(Long bookId, Collection<Status> statuses);

    boolean existsByBook_IdAndStatusIn(Long bookId, Collection<Status> statuses);

    // ===== Bridge cho BorrowService: entity dùng "book", không có "bookId",
    // nhưng BorrowService gọi existsByBookIdAndStatus(bookId, status).
    // Dùng JPQL để khớp đúng chữ ký đang được gọi.
    @Query("select (count(r) > 0) from Reservation r where r.book.id = :bookId and r.status = :status")
    boolean existsByBookIdAndStatus(@Param("bookId") Long bookId, @Param("status") Status status);

    // ===== Dọn READY quá hạn (chuyển sang EXPIRED và ghi thời điểm) =====
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Reservation r
              set r.status = 'EXPIRED',
                  r.expiredAt = :now
            where r.status = 'READY'
              and r.expireAt is not null
              and r.expireAt < :now
           """)
    int expireReadyBefore(@Param("now") LocalDateTime now);
}
