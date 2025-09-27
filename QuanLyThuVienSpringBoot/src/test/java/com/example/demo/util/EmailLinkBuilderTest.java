package com.example.demo.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Skeleton tests for EmailLinkBuilder (activation/reset links). */
@Tag("unit")
class EmailLinkBuilderTest {

    @Test
    @DisplayName("buildResetLink: email contains '+' is encoded correctly")
    void buildResetLink_plusEncoded() {
        // TODO
    }

    @Test
    @DisplayName("buildActivationLink: baseUrl + token + TTL in template")
    void buildActivationLink_containsParts() {
        // TODO
    }
}
