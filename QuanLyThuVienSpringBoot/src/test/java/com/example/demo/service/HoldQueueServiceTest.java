package com.example.demo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** Skeleton tests for HoldQueueService (FIFO + reservation window). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class HoldQueueServiceTest {

    @Test
    @DisplayName("Create holds -> preserves FIFO order")
    void createHolds_fifoOrder() {
        // TODO
    }

    @Test
    @DisplayName("When copy available -> NOTIFY head-of-line with window")
    void notifyHead_withWindow() {
        // TODO
    }

    @Test
    @DisplayName("During window -> only hold owner isAllowedToBorrow")
    void duringWindow_onlyOwnerCanBorrow() {
        // TODO
    }
}
