package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.logging.HandyLogging;

public abstract class ARunnable extends HandyLogging implements Runnable {
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
            logError("Error during scan running", ex);
        }
    }

    public final void startNowInNewThread() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }
}
