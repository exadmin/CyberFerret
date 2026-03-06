package com.github.exadmin.cyberferret.logging;

import com.github.exadmin.cyberferret.utils.ConsoleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandyLogging {


    protected volatile Logger logger;
    protected boolean printToConsole = false;


    public void setPrintToConsole(boolean printToConsole) {
        this.printToConsole = printToConsole;
    }

    public void logError(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().error(msg, binds);
        } else {
            ConsoleUtils.error(msg, binds);
        }
    }

    public void logWarn(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().warn(msg, binds);
        } else {
            ConsoleUtils.warn(msg, binds);
        }
    }

    public void logInfo(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().info(msg, binds);
        } else {
            ConsoleUtils.info(msg, binds);
        }
    }

    public void logDebug(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().debug(msg, binds);
        } else {
            ConsoleUtils.debug(msg, binds);
        }
    }

    public void logTrace(String msg, Object... binds) {
        if (getLogger() != null) {
            getLogger().trace(msg, binds);
        } else {
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
