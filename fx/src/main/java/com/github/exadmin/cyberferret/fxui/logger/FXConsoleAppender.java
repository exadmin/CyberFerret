package com.github.exadmin.cyberferret.fxui.logger;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(
        name = "FXConsoleAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE)
public class FXConsoleAppender extends AbstractAppender {
    public static final List<FXConsoleAppender> MY_INSTANCES = new CopyOnWriteArrayList<>();
    private static final int MAX_QUEUE_SIZE = 10_000;

    private final Queue<String> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicBoolean isServed = new AtomicBoolean(false); // must have true - in case there is a consumer which will "eat" events.

    public FXConsoleAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    @PluginFactory
    public static FXConsoleAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {
        FXConsoleAppender newInstance = new FXConsoleAppender(name, filter, null, true, null);
        MY_INSTANCES.add(newInstance);
        return newInstance;
    }

    /**
     * Adds the newest formatted message without blocking the logging thread.
     * The oldest queued message is discarded when the queue reaches its capacity.
     *
     * @param event log event to enqueue
     */
    @Override
    public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        while (!queue.offer(message)) {
            queue.poll();
        }
    }

    /**
     * Stops this appender and releases its global registration and pending messages.
     *
     * @param timeout maximum time to wait for shutdown
     * @param timeUnit unit of the shutdown timeout
     * @return {@code true} when the appender stops successfully
     */
    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        try {
            return super.stop(timeout, timeUnit);
        } finally {
            MY_INSTANCES.remove(this);
            queue.clear();
        }
    }

    public String popNext() {
        return queue.poll();
    }

    public void setServed(boolean isServed) {
        this.isServed.set(isServed);
    }
}
