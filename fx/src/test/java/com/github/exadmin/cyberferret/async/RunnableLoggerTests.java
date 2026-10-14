package com.github.exadmin.cyberferret.async;

import org.junit.jupiter.api.Test;

import java.util.List;

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
        RunnableLogger logger = new RunnableLogger(null, List.of());
        Thread loggerThread = new Thread(logger);
        loggerThread.start();

        boolean stoppedAfterInterrupt;
        boolean interruptStatusPreserved;
        try {
            long waitDeadline = System.nanoTime() + 1_000_000_000L;
            while (loggerThread.getState() != Thread.State.TIMED_WAITING
                    && loggerThread.isAlive()
                    && System.nanoTime() < waitDeadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.TIMED_WAITING, loggerThread.getState());

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
