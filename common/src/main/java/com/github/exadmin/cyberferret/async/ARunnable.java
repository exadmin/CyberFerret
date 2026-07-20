package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.logging.HandyLogging;

public abstract class ARunnable extends HandyLogging implements Runnable {
    protected volatile Runnable beforeStart;
    protected volatile Runnable afterFinished;
    // when running Scanner in CLI mode - no specific data-store to be rendered in FxUI is collected (amy be additional light operations are executed)
    private final boolean isCLIMode;
    private volatile boolean successful = true;

    public ARunnable(boolean isCLIMode) {
        this.isCLIMode = isCLIMode;
    }

    public void setBeforeStart(Runnable beforeStart) {
        this.beforeStart = beforeStart;
    }
    public void setAfterFinished(Runnable afterFinished) {
        this.afterFinished = afterFinished;
    }

    protected abstract void _run() throws Exception;

    @Override
    public final void run()  {
        successful = true;
        try {
            if (beforeStart != null) beforeStart.run();
            _run();
        } catch (Exception ex) {
            successful = false;
            logError("Error during scan running", ex);
        } finally {
            try {
                if (afterFinished != null) afterFinished.run();
            } catch (Exception ex) {
                successful = false;
                logError("Error during finishing scan running", ex);
            }
        }
    }

    public final void startNowInNewThread() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isCLIMode() {
        return isCLIMode;
    }

    public boolean isSuccessful() {
        return successful;
    }
}
