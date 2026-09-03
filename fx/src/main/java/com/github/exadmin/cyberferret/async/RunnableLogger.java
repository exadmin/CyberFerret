package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.fxui.logger.FXConsoleAppender;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class RunnableLogger implements Runnable {
    private static final int MAX_LOG_SIZE_IN_TEXT_AREA_CHARS = 1024 * 5;

    private long lastTimestamp = 0;
    private static final long MILLIS_MUST_PASSED = 300;

    private volatile boolean stop = false;
    private FXConsoleAppender fxAppender = null;
    private final TextArea textArea;

    public RunnableLogger(TextArea textArea) {
        this.textArea = textArea;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    /**
     * Drains log messages and schedules text-area updates no more than once per configured interval.
     */
    @Override
    public void run() {
        StringBuilder buf = new StringBuilder();

        while (!stop) {
            // check if appender initialized
            if (fxAppender == null && FXConsoleAppender.MY_INSTANCES.isEmpty()) {
                if (!sleep()) {
                    return;
                }
                continue;
            }

            if (fxAppender == null) {
                fxAppender = FXConsoleAppender.MY_INSTANCES.getFirst();
                fxAppender.setServed(true);
            }

            buf.setLength(0);

            for (int i=0; i<100; i++) {
                String text = fxAppender.popNext();
                if (text == null) break;

                buf.append(text).append("\n");
            }

            if (!buf.isEmpty()) {
                long curTime = System.currentTimeMillis();
                long remainingDelay = remainingDelayMillis(lastTimestamp, curTime);
                if (remainingDelay > 0) {
                    if (!sleep(remainingDelay)) {
                        return;
                    }
                    curTime = System.currentTimeMillis();
                }

                String text = buf.toString();
                buf.setLength(0);

                Platform.runLater(() -> {
                    textArea.appendText(text);
                    if (textArea.getLength() > MAX_LOG_SIZE_IN_TEXT_AREA_CHARS * 2) {
                        String currentText = textArea.getText();
                        int newlinePos = currentText.lastIndexOf('\n', currentText.length() - MAX_LOG_SIZE_IN_TEXT_AREA_CHARS);
                        if (newlinePos > 0) {
                            currentText = currentText.substring(newlinePos + 1);
                            textArea.setText(currentText);
                            textArea.selectPositionCaret(currentText.length());
                            textArea.deselect();
                        }
                    }
                });

                lastTimestamp = curTime;
            } else {
                // sleep a moment
                if (!sleep()) {
                    return;
                }
            }
        }
    }

    /**
     * Pauses polling briefly when no log message is available.
     *
     * @return {@code true} when the wait completes, or {@code false} when the thread is interrupted
     */
    private boolean sleep() {
        return sleep(100);
    }

    /**
     * Pauses the logger thread for the requested interval.
     *
     * @param millis number of milliseconds to wait
     * @return {@code true} when the wait completes, or {@code false} when the thread is interrupted
     */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stop = true;
            return false;
        }
    }

    /**
     * Calculates how long the logger must wait before scheduling its next UI update.
     *
     * @param lastUpdateTimestamp timestamp of the previous update, in milliseconds
     * @param currentTimestamp current timestamp, in milliseconds
     * @return remaining delay in milliseconds, or zero when the interval has elapsed
     */
    static long remainingDelayMillis(long lastUpdateTimestamp, long currentTimestamp) {
        long elapsed = currentTimestamp - lastUpdateTimestamp;
        return Math.max(0, MILLIS_MUST_PASSED - elapsed);
    }
}
