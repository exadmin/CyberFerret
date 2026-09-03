package com.github.exadmin.cyberferret.async;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
