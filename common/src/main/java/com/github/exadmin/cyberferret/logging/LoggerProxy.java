package com.github.exadmin.cyberferret.logging;

import com.github.exadmin.cyberferret.utils.ConsoleUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoggerProxy {
    private static final Map<Class<?>, Object> LOGGER_BY_CLASS = new ConcurrentHashMap<>();
    private static volatile Object loggerFactory;

    private final Class<?> ownerClass;

    public LoggerProxy(Class<?> ownerClass) {
        this.ownerClass = ownerClass;
    }

    public static void injectLoggerFactory(Object factory) {
        loggerFactory = factory;
    }

    public static void injectLogger(Class<?> ownerClass, Object logger) {
        if (ownerClass == null || logger == null) {
            return;
        }
        LOGGER_BY_CLASS.put(ownerClass, logger);
    }

    public void error(String msg, Object... binds) {
        if (!tryLog("error", msg, binds)) {
            ConsoleUtils.error(msg, binds);
        }
    }

    public void warn(String msg, Object... binds) {
        if (!tryLog("warn", msg, binds)) {
            ConsoleUtils.warn(msg, binds);
        }
    }

    public void info(String msg, Object... binds) {
        if (!tryLog("info", msg, binds)) {
            ConsoleUtils.info(msg, binds);
        }
    }

    public void debug(String msg, Object... binds) {
        if (!tryLog("debug", msg, binds)) {
            ConsoleUtils.debug(msg, binds);
        }
    }

    public void trace(String msg, Object... binds) {
        if (!tryLog("trace", msg, binds)) {
            ConsoleUtils.trace(msg, binds);
        }
    }

    private boolean tryLog(String method, String msg, Object... binds) {
        Object logger = resolveLogger();
        if (logger == null) {
            return false;
        }
        return invokeLogger(logger, method, msg, binds);
    }

    private Object resolveLogger() {
        Object logger = LOGGER_BY_CLASS.get(ownerClass);
        if (logger != null) {
            return logger;
        }

        Object factory = loggerFactory;
        if (factory == null) {
            return null;
        }

        logger = createLogger(factory, ownerClass);
        if (logger != null) {
            LOGGER_BY_CLASS.put(ownerClass, logger);
        }
        return logger;
    }

    private static Object createLogger(Object factory, Class<?> ownerClass) {
        try {
            Method m = factory.getClass().getMethod("getLogger", Class.class);
            return m.invoke(factory, ownerClass);
        } catch (ReflectiveOperationException ignored) {
            // fallback to String-based factory
        }

        try {
            Method m = factory.getClass().getMethod("getLogger", String.class);
            return m.invoke(factory, ownerClass.getName());
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeLogger(Object logger, String method, String msg, Object... binds) {
        try {
            Method m = logger.getClass().getMethod(method, String.class, Object[].class);
            m.invoke(logger, msg, binds);
            return true;
        } catch (ReflectiveOperationException ignored) {
            // try narrower signatures
        }

        if (binds == null || binds.length == 0) {
            try {
                Method m = logger.getClass().getMethod(method, String.class);
                m.invoke(logger, msg);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        if (binds.length == 1 && binds[0] instanceof Throwable throwable) {
            try {
                Method m = logger.getClass().getMethod(method, String.class, Throwable.class);
                m.invoke(logger, msg, throwable);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        return false;
    }
}
