package com.example.demo.service;

import com.example.demo.model.Book;
import com.example.demo.model.Reservation;
import com.example.demo.model.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.ReservationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Đặt chỗ:
 * - Chỉ cho phép tạo khi kho hết (available == 0).
 * - Khi có lại đúng 1 cuốn: giữ 24h cho người đã đặt chỗ theo FIFO.
 * - Khi có lại >= 2 cuốn: không chặn ai (chỉ thông báo).
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepo;
    private final BorrowRepository borrowRepo;
    private final BookRepository bookRepo;

    public ReservationService(ReservationRepository reservationRepo,
                              BorrowRepository borrowRepo,
                              BookRepository bookRepo) {
        this.reservationRepo = reservationRepo;
        this.borrowRepo = borrowRepo;
        this.bookRepo = bookRepo;
    }

    /* ===================== TẠO ĐẶT CHỖ ===================== */
    @Transactional
    public Reservation create(User user, Long bookId) {
        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sách id=" + bookId));

        // quantity có thể là Integer (wrapper) -> xử lý null an toàn
        Integer qObj = book.getQuantity();
        int totalQty = (qObj == null ? 0 : qObj.intValue());

        long opened    = borrowRepo.countOpenByBookId(bookId);   // số bản mượn chưa trả
        long available = Math.max(0, totalQty - opened);

        if (available > 0) {
            throw new IllegalStateException("Sách đang còn trong kho, không cần đặt chỗ. Bạn có thể mượn ngay.");
        }

        boolean existsMine = reservationRepo.existsByUser_IdAndBook_IdAndStatusIn(
                user.getId(), bookId, List.of(Reservation.Status.PENDING, Reservation.Status.READY)
        );
        if (existsMine) {
            throw new IllegalStateException("Bạn đã có yêu cầu đặt chỗ cho cuốn này.");
        }

        Reservation r = new Reservation();
        r.setUser(user);
        r.setBook(book);
        r.setStatus(Reservation.Status.PENDING);
        // KHÔNG gọi r.setCreatedAt(...): nếu entity dùng @CreationTimestamp thì DB sẽ tự set
        return reservationRepo.save(r);
    }

    /* ============== HỦY ĐẶT CHỖ BỞI CHÍNH CHỦ =============== */
    @Transactional
    public void cancelByOwner(User user, Long reservationId) {
        Reservation r = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Đặt chỗ không tồn tại"));

        if (!r.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Bạn không thể hủy đặt chỗ của người khác.");
        }
        if (r.getStatus() == Reservation.Status.FULFILLED
                || r.getStatus() == Reservation.Status.CANCELLED
                || r.getStatus() == Reservation.Status.EXPIRED) {
            return;
        }

        r.setStatus(Reservation.Status.CANCELLED);
        r.setCancelledAt(LocalDateTime.now());
        reservationRepo.save(r);
    }

    /* ========== LUẬT ƯU TIÊN KHI TẠO MÃ MƯỢN ========== */
    @Transactional
    public CanBorrowResult assertBorrowAllowed(Long bookId, Long userId) {
        // dọn các READY quá hạn
        reservationRepo.expireReadyBefore(LocalDateTime.now());

        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sách id=" + bookId));

        Integer qObj = book.getQuantity();
        int totalQty = (qObj == null ? 0 : qObj.intValue());
        long opened  = borrowRepo.countOpenByBookId(bookId);
        long available = Math.max(0, (long) totalQty - opened);

        if (available >= 2) {
            // có >= 2 bản: không chặn ai
            return CanBorrowResult.ok();
        }

        if (available == 1) {
            // 1) Có ai đang READY hợp lệ chưa?
            var readyList = reservationRepo.findByBook_IdAndStatusIn(
                    bookId, List.of(Reservation.Status.READY)
            );
            readyList.removeIf(r -> r.getExpireAt() != null && r.getExpireAt().isBefore(LocalDateTime.now()));

            if (!readyList.isEmpty()) {
                boolean mine = readyList.stream().anyMatch(r -> r.getUser().getId().equals(userId));
                return mine
                        ? CanBorrowResult.ok()
                        : CanBorrowResult.block("Cuốn này đang được giữ 24 giờ cho người đã đặt chỗ trước. Vui lòng quay lại sau.");
            }

            // 2) Chưa ai READY → promote người PENDING lâu nhất
            var oldestPendingOpt = reservationRepo.findFirstByBook_IdAndStatusInOrderByCreatedAtAsc(
                    bookId, List.of(Reservation.Status.PENDING)
            );
            if (oldestPendingOpt.isPresent()) {
                var oldestPending = oldestPendingOpt.get();
                oldestPending.setStatus(Reservation.Status.READY);
                oldestPending.setReadyAt(LocalDateTime.now());
                oldestPending.setExpireAt(LocalDateTime.now().plusDays(1)); // giữ 24h
                reservationRepo.save(oldestPending);

                // nếu người được promote không phải mình → chặn
                if (!oldestPending.getUser().getId().equals(userId)) {
                    return CanBorrowResult.block("Cuốn này vừa được giữ 24 giờ cho người đã đặt chỗ trước. Bạn sẽ mượn được khi hết thời gian giữ.");
                }
                // chính mình → cho mượn
                return CanBorrowResult.ok();
            }

            // 3) Không có PENDING nào → cho mượn bình thường
            return CanBorrowResult.ok();
        }

        // available == 0 → để luồng tạo vé xử lý riêng
        return CanBorrowResult.ok();
    }

    /* ========== Khi mượn thành công, đánh dấu FULFILLED nếu có ========== */
    @Transactional
    public void markFulfilledIfAny(Long bookId, Long userId) {
        reservationRepo.findByUser_IdAndBook_IdAndStatusIn(
                userId, bookId, List.of(Reservation.Status.READY, Reservation.Status.PENDING)
        ).ifPresent(r -> {
            r.setStatus(Reservation.Status.FULFILLED);
            r.setFulfilledAt(LocalDateTime.now());
            reservationRepo.save(r);
        });
    }

    /* ========== Banner: trả về READY còn hiệu lực của user (nếu có) ========== */
    @Transactional
    public Optional<ReadyNotice> getOrPromoteReadyForUser(Long userId) {
        reservationRepo.expireReadyBefore(LocalDateTime.now());

        // Ưu tiên READY hiện có
        var myReady = reservationRepo.findAllByUser_IdAndStatusIn(
                userId, List.of(Reservation.Status.READY)
        );
        myReady.removeIf(r -> r.getExpireAt() != null && r.getExpireAt().isBefore(LocalDateTime.now()));
        if (!myReady.isEmpty()) {
            var r = myReady.get(0);
            return Optional.of(new ReadyNotice(
                    r.getId(),
                    r.getBook().getId(),
                    r.getBook().getTitle(),
                    r.getExpireAt()
            ));
        }

        // Nếu chưa có, thử promote từ PENDING khi hiện tại sách chỉ còn 1
        var myPendings = reservationRepo.findAllByUser_IdAndStatusIn(
                userId, List.of(Reservation.Status.PENDING)
        );
        for (var p : myPendings) {
            var gate = assertBorrowAllowed(p.getBook().getId(), userId);
            if (gate.allowed()) {
                var nowReady = reservationRepo.findByUser_IdAndBook_IdAndStatusIn(
                        userId, p.getBook().getId(), List.of(Reservation.Status.READY)
                );
                if (nowReady.isPresent()) {
                    var r = nowReady.get();
                    return Optional.of(new ReadyNotice(
                            r.getId(),
                            r.getBook().getId(),
                            r.getBook().getTitle(),
                            r.getExpireAt()
                    ));
                }
            }
        }
        return Optional.empty();
    }

    /* ===== Helper records ===== */
    public record CanBorrowResult(boolean allowed, String reason) {
        // Đổi tên factory để KHÔNG trùng accessor 'allowed()'
        public static CanBorrowResult ok() { return new CanBorrowResult(true, null); }
        public static CanBorrowResult block(String reason) { return new CanBorrowResult(false, reason); }
    }
    public record ReadyNotice(Long reservationId, Long bookId, String bookTitle, LocalDateTime expireAt) {}
}
