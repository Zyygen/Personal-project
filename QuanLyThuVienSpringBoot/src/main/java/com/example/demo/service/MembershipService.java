package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MembershipService {

    private final UserRepository userRepo;

    public MembershipService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public boolean isMember(User u) {
        if (u == null) return false;
        LocalDateTime until = u.getMemberUntil();
        return until != null && until.isAfter(LocalDateTime.now());
    }

    public LocalDateTime previewAfterMonthly(User u) {
        return previewAfterMonthly(u, 1);
    }

    public LocalDateTime previewAfterMonthly(User u, int months) {
        if (u == null) return null;
        if (months < 1) months = 1;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = (u.getMemberUntil() != null && u.getMemberUntil().isAfter(now))
                ? u.getMemberUntil()
                : now;
        return base.plusMonths(months);
    }

    @Transactional
    public void extendMonthly(Long userId) {
        extendMonthly(userId, 1);
    }

    @Transactional
    public void extendMonthly(Long userId, int months) {
        if (months < 1) months = 1;
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = (u.getMemberUntil() != null && u.getMemberUntil().isAfter(now))
                ? u.getMemberUntil()
                : now;
        u.setMemberUntil(base.plusMonths(months));
        userRepo.save(u);
    }
}
