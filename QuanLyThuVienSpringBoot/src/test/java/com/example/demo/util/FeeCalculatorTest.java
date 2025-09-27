package com.example.demo.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Skeleton tests for FeeCalculator (late fee computation). */
@Tag("unit")
class FeeCalculatorTest {

    @Test
    @DisplayName("fee: daysLate = 0 -> 0")
    void fee_zeroDays_returnsZero() {
        // TODO: Arrange/Act/Assert using your FeeCalculator
    }

    @Test
    @DisplayName("fee: negative days -> throws")
    void fee_negativeDays_throws() {
        // TODO
    }

    @Test
    @DisplayName("fee: positive days -> expected amount (rounding)")
    void fee_positiveDays_ok() {
        // TODO
    }
}
