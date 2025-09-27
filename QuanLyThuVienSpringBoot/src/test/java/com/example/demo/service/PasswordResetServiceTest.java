package com.example.demo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** Skeleton tests for PasswordResetService (token lifecycle & rate limit). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PasswordResetServiceTest {

    @Test
    @DisplayName("request reset -> invalidates old tokens of same user")
    void requestReset_invalidatesOld() {
        // TODO
    }

    @Test
    @DisplayName("reset with valid token -> bcrypt & revoke token")
    void reset_validToken_updatesPassword() {
        // TODO
    }

    @Test
    @DisplayName("rate limit: too many requests -> blocked")
    void rateLimit_blocks() {
        // TODO
    }
}
