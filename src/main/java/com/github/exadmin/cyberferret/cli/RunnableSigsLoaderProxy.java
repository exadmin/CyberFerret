package com.github.exadmin.cyberferret.cli;

import com.github.exadmin.cyberferret.async.RunnableSigsLoader;
import com.github.exadmin.cyberferret.utils.ConsoleUtils;

public class RunnableSigsLoaderProxy extends RunnableSigsLoader {
    @Override
    public void logError(String msg, Object... binds) {
        ConsoleUtils.error(msg, binds);
    }

    @Override
    public void logWarn(String msg, Object... binds) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public void logInfo(String msg, Object... binds) {
        ConsoleUtils.info(msg, binds);
    }

    @Override
    public void logDebug(String msg, Object... binds) {
        ConsoleUtils.debug(msg, binds);
    }

    @Override
    public void logTrace(String msg, Object... binds) {
        throw new IllegalStateException("Not implemented yet");
    }
}
