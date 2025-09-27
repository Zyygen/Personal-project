package com.example.demo.repository;

import com.example.demo.model.BorrowTicket;
import com.example.demo.model.BorrowTicket.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BorrowTicketRepository extends JpaRepository<BorrowTicket, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<BorrowTicket> findByToken(String token); // dùng để confirm, có khóa

	Optional<BorrowTicket> findByIdAndUserId(Long id, Long userId); // dùng cho API check status

    // Nạp kèm book để tránh LazyInitializationException khi render view
    @EntityGraph(attributePaths = {"book"})
    Optional<BorrowTicket> findWithBookById(Long id);

    // Khóa bản ghi khi admin xác nhận -> tránh double-confirm
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BorrowTicket t where t.id = :id")
    Optional<BorrowTicket> findByIdForUpdate(@Param("id") Long id);

    // Lấy toàn bộ vé đang PENDING của user (trừ cái hiện tại nếu cần)
    List<BorrowTicket> findByUserIdAndStatusOrderByRequestedAtAsc(Long userId, BorrowTicket.TicketStatus status);
    
    /* Lọc theo user + trạng thái (list) */
    List<BorrowTicket> findByUserIdAndStatus(Long userId, TicketStatus status);
    List<BorrowTicket> findByUserIdAndStatusOrderByRequestedAtDesc(Long userId, TicketStatus status);
    List<BorrowTicket> findByUserIdOrderByRequestedAtDesc(Long userId);

    /* Phân trang: nạp sẵn book */
    @EntityGraph(attributePaths = { "book" })
    Page<BorrowTicket> findByUserIdAndStatus(Long userId, TicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "book" })
    Page<BorrowTicket> findByUserIdAndStatusNot(Long userId, TicketStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BorrowTicket t where t.token = :token")
    Optional<BorrowTicket> findWithLockByToken(@Param("token") String token);

    /* Tổng số lượng từ các vé còn hiệu lực & chưa bị huỷ (để “giữ chỗ” khi user đã tạo QR) */
    @Query("""
           select coalesce(sum(bt.amount), 0)
           from BorrowTicket bt
           where bt.user.id = :userId
             and bt.status in :statuses
             and (bt.expiresAt is null or bt.expiresAt > CURRENT_TIMESTAMP)
           """)
    int sumAmountForUserAndStatuses(@Param("userId") Long userId,
                                    @Param("statuses") Collection<TicketStatus> statuses);

    /* Cập nhật trạng thái có điều kiện (PENDING -> CONFIRMED, v.v.) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update BorrowTicket t
              set t.status = :newStatus
            where t.id     = :id
              and t.status = :expected
           """)
    int updateStatusIf(@Param("id") Long id,
                       @Param("expected") TicketStatus expected,
                       @Param("newStatus") TicketStatus newStatus);

    /* Hết hạn vé chờ (cron) — dùng hằng enum để an toàn với EnumType */
    @Modifying
    @Query("""
           update BorrowTicket t
              set t.status = com.example.demo.model.BorrowTicket$TicketStatus.EXPIRED
            where t.status   = com.example.demo.model.BorrowTicket$TicketStatus.PENDING
              and t.expiresAt < CURRENT_TIMESTAMP
           """)
    int expireOldTickets();
}
