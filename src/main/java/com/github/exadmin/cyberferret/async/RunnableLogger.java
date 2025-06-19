package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.fxui.logger.FXConsoleAppender;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class RunnableLogger implements Runnable {
    private static final int MAX_LOG_SIZE_IN_TEXT_AREA_CHARS = 1024 * 5;
    private static final int CUT_PORTION = MAX_LOG_SIZE_IN_TEXT_AREA_CHARS / 10;

    private boolean stop = false;
    private FXConsoleAppender fxAppender = null;
    private final TextArea textArea;

    public RunnableLogger(TextArea textArea) {
        this.textArea = textArea;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    @Override
    public void run() {
        StringBuilder buf = new StringBuilder();

        while (!stop) {
            // check if appender initialized
            if (fxAppender == null && FXConsoleAppender.MY_INSTANCES.isEmpty()) {
                sleep();
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
                String text = buf.toString();
                buf.setLength(0);

                Platform.runLater(() -> {
                    textArea.appendText(text);
                    if (textArea.getText().length() > MAX_LOG_SIZE_IN_TEXT_AREA_CHARS) {
                        String currentText = textArea.getText();
                        currentText = currentText.substring(CUT_PORTION);
                        int newLineIndex = currentText.indexOf("\n");
                        currentText = currentText.substring(newLineIndex + 1);
                        currentText = "...\n" + currentText;
                        textArea.setText(currentText);
                        textArea.selectPositionCaret(currentText.length());
                        textArea.deselect();
                    }
                });
            } else {
                // sleep a moment
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
