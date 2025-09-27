package com.example.demo.repository;

import com.example.demo.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);
}
