package com.example.demo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** Skeleton tests for IpnGuard/PaymentDecision (VNPay IPN handling). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class IpnGuardTest {

    @Test
    @DisplayName("IPN SUCCESS -> payment SUCCESS, unlock debt when total=0")
    void ipn_success_updatesPaymentAndUnlocks() {
        // TODO: mock repo/services; verify state transitions
    }

    @Test
    @DisplayName("IPN invalid signature -> payment FAILED; no unlock")
    void ipn_invalidSignature_fails() {
        // TODO
    }

    @Test
    @DisplayName("Return URL must NOT change payment state (IPN-only source of truth)")
    void returnUrl_doesNotMutatePayment() {
        // TODO
    }
}
