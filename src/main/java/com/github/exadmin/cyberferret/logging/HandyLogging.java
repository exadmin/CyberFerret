package com.github.exadmin.cyberferret.logging;

import com.github.exadmin.cyberferret.utils.ConsoleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandyLogging {
    private static final int LEVEL_ERROR = 0;
    private static final int LEVEL_WARN  = 1;
    private static final int LEVEL_INFO  = 2;
    private static final int LEVEL_DEBUG = 3;
    private static final int LEVEL_TRACE = 4;

    protected volatile Logger logger;
    protected boolean printToConsole = false;
    protected int loggingLevel = LEVEL_INFO;

    public void setPrintToConsole(boolean printToConsole) {
        this.printToConsole = printToConsole;
    }

    public void logError(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().error(msg, binds);
        } else if (loggingLevel >= LEVEL_ERROR) {
            ConsoleUtils.error(msg, binds);
        }
    }

    public void logWarn(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().warn(msg, binds);
        } else if (loggingLevel >= LEVEL_WARN) {
            ConsoleUtils.warn(msg, binds);
        }
    }

    public void logInfo(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().info(msg, binds);
        } else if (loggingLevel >= LEVEL_INFO) {
            ConsoleUtils.info(msg, binds);
        }
    }

    public void logDebug(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().debug(msg, binds);
        } else if (loggingLevel >= LEVEL_DEBUG) {
            ConsoleUtils.debug(msg, binds);
        }
    }

    public void logTrace(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().trace(msg, binds);
        } else if (loggingLevel >= LEVEL_TRACE) {
            ConsoleUtils.trace(msg, binds);
        }
    }

    public final Logger getLogger() {
        if (printToConsole) return null;

        if (logger == null) {
            logger = LoggerFactory.getLogger(getClass());
        }

        return logger;
    }
}
