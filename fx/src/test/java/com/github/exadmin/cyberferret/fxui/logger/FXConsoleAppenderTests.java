package com.github.exadmin.cyberferret.fxui.logger;

import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
