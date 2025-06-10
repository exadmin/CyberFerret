package com.github.exadmin.sourcesscanner.fxui.logger;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(
        name = "FXConsoleAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE)
public class FXConsoleAppender extends AbstractAppender {
    public static final List<FXConsoleAppender> MY_INSTANCES = Collections.synchronizedList(new ArrayList<>());

    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
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

    @Override
    public void append(LogEvent event) {
        if (isServed.get()) {
            queue.add(event.getMessage().getFormattedMessage());
        }
    }

    public String popNext() {
        return queue.poll();
    }

    public void setServed(boolean isServed) {
        this.isServed.set(isServed);
    }
}
