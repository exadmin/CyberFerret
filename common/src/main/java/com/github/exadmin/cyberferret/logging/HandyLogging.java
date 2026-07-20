package com.github.exadmin.cyberferret.logging;

import com.github.exadmin.cyberferret.utils.ConsoleUtils;

public class HandyLogging {
    private final LoggerProxy loggerProxy = new LoggerProxy(getClass());
    protected boolean printToConsole = false;
    private boolean silent = false;


    public void setPrintToConsole(boolean printToConsole) {
        this.printToConsole = printToConsole;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public void logError(String msg, Object... binds) {
        if (silent) return;
        if (printToConsole) {
            ConsoleUtils.error(msg, binds);
        } else {
            loggerProxy.error(msg, binds);
        }
    }

    public void logWarn(String msg, Object... binds) {
        if (silent) return;
        if (printToConsole) {
            ConsoleUtils.warn(msg, binds);
        } else {
            loggerProxy.warn(msg, binds);
        }
    }

    public void logInfo(String msg, Object... binds) {
        if (silent) return;
        if (printToConsole) {
            ConsoleUtils.info(msg, binds);
        } else {
            loggerProxy.info(msg, binds);
        }
    }

    public void logDebug(String msg, Object... binds) {
        if (silent) return;
        if (printToConsole) {
            ConsoleUtils.debug(msg, binds);
        } else {
            loggerProxy.debug(msg, binds);
        }
    }

    public void logTrace(String msg, Object... binds) {
        if (silent) return;
        if (printToConsole) {
            ConsoleUtils.trace(msg, binds);
        } else {
            loggerProxy.trace(msg, binds);
        }
    }
}
