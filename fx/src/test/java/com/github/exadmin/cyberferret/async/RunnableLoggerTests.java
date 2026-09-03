package com.github.exadmin.cyberferret.async;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunnableLoggerTests {
    @Test
    void calculatesDelayRequiredToEnforceUpdateInterval() {
        assertEquals(200, RunnableLogger.remainingDelayMillis(1_000, 1_100));
        assertEquals(0, RunnableLogger.remainingDelayMillis(1_000, 1_300));
        assertEquals(0, RunnableLogger.remainingDelayMillis(1_000, 1_400));
    }

    @Test
    void exitsAndPreservesInterruptStatusWhenSleepIsInterrupted() throws InterruptedException {
        RunnableLogger logger = new RunnableLogger(null);
        Thread loggerThread = new Thread(logger);
        loggerThread.start();

        long waitDeadline = System.currentTimeMillis() + 1_000;
        while (loggerThread.getState() != Thread.State.TIMED_WAITING
                && System.currentTimeMillis() < waitDeadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.TIMED_WAITING, loggerThread.getState());

        boolean stoppedAfterInterrupt;
        boolean interruptStatusPreserved;
        try {
            loggerThread.interrupt();
            loggerThread.join(1_000);
            stoppedAfterInterrupt = !loggerThread.isAlive();
            interruptStatusPreserved = loggerThread.isInterrupted();
        } finally {
            logger.setStop(true);
            loggerThread.interrupt();
            loggerThread.join(1_000);
        }

        assertFalse(loggerThread.isAlive());
        assertTrue(stoppedAfterInterrupt);
        assertTrue(interruptStatusPreserved);
    }

    /**
     * Verifies that layout-provided line separators are not duplicated while events are batched for the UI.
     */
    @Test
    void appendsFormattedEventsWithoutExtraLineBreaks() {
        StringBuilder buffer = new StringBuilder();

        RunnableLogger.appendFormattedEvent(buffer, "first event\n");
        RunnableLogger.appendFormattedEvent(buffer, "second event\n");

        assertEquals("first event\nsecond event\n", buffer.toString());
    }
}
