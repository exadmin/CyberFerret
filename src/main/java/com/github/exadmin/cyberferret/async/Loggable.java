package com.github.exadmin.cyberferret.async;

import org.slf4j.Logger;

public interface Loggable {
    Logger getLog();
    void logError(String msg, Object ... binds);
    void logWarn(String msg, Object ... binds);
    void logInfo(String msg, Object ... binds);
    void logDebug(String msg, Object ... binds);
    void logTrace(String msg, Object ... binds);
}
