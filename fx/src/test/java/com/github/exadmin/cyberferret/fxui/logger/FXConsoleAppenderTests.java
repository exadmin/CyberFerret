package com.github.exadmin.cyberferret.fxui.logger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FXConsoleAppenderTests {
    @Test
    void dropsOldestMessageWhenQueueIsFull() {
        FXConsoleAppender appender = new FXConsoleAppender("test", null, null, true, null);

        for (int index = 0; index <= 10_000; index++) {
            appender.append(Log4jLogEvent.newBuilder()
                    .setMessage(new SimpleMessage("message-" + index))
                    .build());
        }

        List<String> messages = new ArrayList<>();
        String message;
        while ((message = appender.popNext()) != null) {
            messages.add(message);
        }

        assertEquals(10_000, messages.size());
        assertEquals("message-1", messages.getFirst());
        assertEquals("message-10000", messages.getLast());
    }

    @Test
    void releasesRegistrationAndMessagesWhenStopped() {
        FXConsoleAppender appender = FXConsoleAppender.createAppender("test", null, null);
        appender.append(Log4jLogEvent.newBuilder()
                .setMessage(new SimpleMessage("pending message"))
                .build());

        appender.stop();

        assertFalse(FXConsoleAppender.MY_INSTANCES.contains(appender));
        assertNull(appender.popNext());
    }

    @Test
    void formatsEventsWithConfiguredLayout() {
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%level %msg%n%throwable")
                .build();
        FXConsoleAppender appender = new FXConsoleAppender("test", null, layout, true, null);

        appender.append(Log4jLogEvent.newBuilder()
                .setLevel(Level.ERROR)
                .setMessage(new SimpleMessage("operation failed"))
                .setThrown(new IllegalStateException("failure details"))
                .build());

        String formattedEvent = appender.popNext();
        assertTrue(formattedEvent.contains("ERROR operation failed"));
        assertTrue(formattedEvent.contains("IllegalStateException: failure details"));
    }

    /**
     * Verifies that the configured UI appender receives application events but excludes dependency events.
     */
    @Test
    void routesOnlyApplicationEventsToUiAppender() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        FXConsoleAppender appender = context.getConfiguration().getAppender("FXConsoleAppender");
        drain(appender);

        LogManager.getLogger("third.party.Dependency").info("dependency routing probe");
        LogManager.getLogger("com.github.exadmin.cyberferret.RoutingProbe").info("application routing probe");

        List<String> messages = drain(appender);
        assertFalse(messages.stream().anyMatch(message -> message.contains("dependency routing probe")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("application routing probe")));
    }

    /**
     * Removes and returns all pending messages from an appender without affecting its lifecycle.
     *
     * @param appender appender to drain
     * @return pending messages in queue order
     */
    private static List<String> drain(FXConsoleAppender appender) {
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = appender.popNext()) != null) {
            messages.add(message);
        }
        return messages;
    }
}
