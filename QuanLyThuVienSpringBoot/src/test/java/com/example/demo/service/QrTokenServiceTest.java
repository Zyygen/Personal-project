package com.example.demo.service;

import com.example.demo.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

/** Skeleton tests for QrTokenService (one-time, TTL). */
@Tag("unit")
class QrTokenServiceTest {

    final Clock fixed = TestFixtures.fixedClock();

    @Test
    @DisplayName("issue -> validate -> consume -> validate fails (one-time use)")
    void oneTimeUse_flow() {
        // TODO
    }

    @Test
    @DisplayName("expired token -> throws")
    void expired_throws() {
        // TODO
    }
}
