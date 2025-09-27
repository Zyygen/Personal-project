package com.example.demo.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

/** Skeleton tests for VnPaySignatureUtil (sign & verify). */
@Tag("unit")
class VnPaySignatureUtilTest {

    @Test
    @DisplayName("sign/verify: canonicalized params -> true")
    void sign_thenVerify_true() {
        // TODO: Arrange params in TreeMap, sign, then verify
    }

    @Test
    @DisplayName("verify: mismatched amount -> false")
    void verify_amountMismatch_false() {
        // TODO
    }

    @Test
    @DisplayName("verify: missing or invalid signature -> false")
    void verify_invalidSignature_false() {
        // TODO
    }
}
