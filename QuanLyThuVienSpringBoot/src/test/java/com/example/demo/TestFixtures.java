package com.example.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Common fixtures for deterministic time-based tests. */
public final class TestFixtures {
    private TestFixtures() {}

    /** 2025-09-21T00:00:00Z fixed clock for reproducible tests. */
    public static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2025-09-21T00:00:00Z"), ZoneOffset.UTC);
    }
}
