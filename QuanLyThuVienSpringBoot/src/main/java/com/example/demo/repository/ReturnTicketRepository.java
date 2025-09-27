package com.example.demo.repository;

import com.example.demo.model.ReturnTicket;
import com.example.demo.model.ReturnTicket.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnTicketRepository extends JpaRepository<ReturnTicket, Long> {

    /* ===== Tra cứu theo token ===== */
    Optional<ReturnTicket> findByToken(String token);

    /* Khóa bản ghi theo token để chống quét 2 lần (double-scan) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReturnTicket t where t.token = :token")
    Optional<ReturnTicket> findWithLockByToken(@Param("token") String token);

    /* ===== Quyền theo user (đi qua borrow.user) ===== */
    Optional<ReturnTicket> findByIdAndBorrow_User_Id(Long id, Long userId);

    /* ===== findById kèm EntityGraph để tránh LazyInitialization ===== */
    @EntityGraph(attributePaths = {"borrow", "borrow.book", "borrow.user"})
    Optional<ReturnTicket> findById(Long id);

    /* ===== Danh sách & phân trang (nạp sẵn borrow & book) ===== */
    List<ReturnTicket> findByStatusOrderByRequestedAtDesc(TicketStatus status);

    List<ReturnTicket> findByBorrow_User_IdOrderByRequestedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"borrow", "borrow.book"})
    Page<ReturnTicket> findByBorrow_User_IdAndStatusOrderByRequestedAtDesc(
            Long userId, TicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"borrow", "borrow.book"})
    Page<ReturnTicket> findByBorrow_User_IdAndStatusNotOrderByRequestedAtDesc(
            Long userId, TicketStatus status, Pageable pageable);

    // phiên bản KHÔNG OrderBy để sort qua Pageable từ Controller
    @EntityGraph(attributePaths = {"borrow", "borrow.book"})
    Page<ReturnTicket> findByBorrow_User_IdAndStatus(
            Long userId, TicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"borrow", "borrow.book"})
    Page<ReturnTicket> findByBorrow_User_IdAndStatusNot(
            Long userId, TicketStatus status, Pageable pageable);

    /* Truy vấn tiện lợi: theo user + status, order theo requestedAt */
    List<ReturnTicket> findByBorrow_User_IdAndStatusOrderByRequestedAtDesc(Long userId, TicketStatus status);

    /* ===== Update trạng thái có điều kiện (PENDING -> CONFIRMED, ...) ===== */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update ReturnTicket t
              set t.status = :newStatus
            where t.id     = :id
              and t.status = :expected
           """)
    int updateStatusIf(@Param("id") Long id,
                       @Param("expected") TicketStatus expected,
                       @Param("newStatus") TicketStatus newStatus);

    /* ===== Hết hạn vé chờ ===== */
    @Modifying
    @Query("""
           update ReturnTicket t
              set t.status = 'EXPIRED'
            where t.status   = 'PENDING'
              and t.expiresAt < CURRENT_TIMESTAMP
           """)
    int expireOldTickets();
}
