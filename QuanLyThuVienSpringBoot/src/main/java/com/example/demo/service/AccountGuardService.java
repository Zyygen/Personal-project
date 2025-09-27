package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.BorrowRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountGuardService {
  private final BorrowRepository borrowRepo;
  private final UserRepository userRepo;

  public AccountGuardService(BorrowRepository b, UserRepository u) {
    this.borrowRepo = b; this.userRepo = u;
  }

  @Transactional
  public void refreshStatus(User user) {
    var list = borrowRepo.findByUserIdAndReturnDateIsNull(user.getId());
    int maxOver = 0;
    boolean anyOver = false;
    for (var br : list) {
      int d = br.calcOverdueDays();
      maxOver = Math.max(maxOver, d);
      if (d > 0) anyOver = true;
    }
    if (maxOver >= 30) user.setStatus(User.AccountStatus.BANNED);
    else if (anyOver)  user.setStatus(User.AccountStatus.SUSPENDED);
    else               user.setStatus(User.AccountStatus.ACTIVE);
    userRepo.save(user);
  }
}
