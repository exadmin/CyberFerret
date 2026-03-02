package com.github.exadmin.cyberferret.async;

import org.slf4j.Logger;

public abstract class ARunnable implements Runnable, Loggable {
    protected Runnable beforeStart;
    protected Runnable afterFinished;

    public void setBeforeStart(Runnable beforeStart) {
        this.beforeStart = beforeStart;
    }

    public void setAfterFinished(Runnable afterFinished) {
        this.afterFinished = afterFinished;
    }

    protected abstract void _run() throws Exception;


    @Override
    public final void run()  {
        try {
            if (beforeStart != null) beforeStart.run();
            _run();
            if (afterFinished != null) afterFinished.run();
        } catch (Exception ex) {
            getLog().error("Error during scan running", ex);
        }
    }

    public final void startNowInNewThread() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void logError(String msg, Object... binds) {
        if (getLog() != null) getLog().error(msg, binds);
    }

    @Override
    public void logWarn(String msg, Object... binds) {
        if (getLog() != null) getLog().warn(msg, binds);
    }

    @Override
    public void logInfo(String msg, Object... binds) {
        if (getLog() != null) getLog().info(msg, binds);
    }

    @Override
    public void logDebug(String msg, Object... binds) {
        if (getLog() != null) getLog().debug(msg, binds);
    }

    @Override
    public void logTrace(String msg, Object... binds) {
        if (getLog() != null) getLog().trace(msg, binds);
    }
}
