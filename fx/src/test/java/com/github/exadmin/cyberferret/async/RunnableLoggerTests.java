package com.github.exadmin.cyberferret.async;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunnableLoggerTests {
    @Test
    void calculatesDelayRequiredToEnforceUpdateInterval() {
        assertEquals(200, RunnableLogger.remainingDelayMillis(1_000, 1_100));
        assertEquals(0, RunnableLogger.remainingDelayMillis(1_000, 1_300));
        assertEquals(0, RunnableLogger.remainingDelayMillis(1_000, 1_400));
    }
}
