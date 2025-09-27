package com.example.demo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Skeleton tests for BorrowEligibilityService (limit, debt-lock, hold coverage). */
@Tag("unit")
class BorrowEligibilityServiceTest {

    @Test
    @DisplayName("member within limit & no debt & no hold coverage -> allowed")
    void allowed_whenWithinRules() {
        // TODO
    }

    @Test
    @DisplayName("locked by debt -> denied with reason")
    void lockedByDebt_denied() {
        // TODO
    }

    @Test
    @DisplayName("copy covered by other's hold window -> denied")
    void holdCoverage_denied() {
        // TODO
    }
}
