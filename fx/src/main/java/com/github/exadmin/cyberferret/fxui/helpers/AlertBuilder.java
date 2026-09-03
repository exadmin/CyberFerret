package com.github.exadmin.cyberferret.fxui.helpers;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlertBuilder {
    private static final Logger log = LoggerFactory.getLogger(AlertBuilder.class);

    public static void showInfo(String text) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(text);

            log.info(text);
            alert.showAndWait();
        });

    }

    public static void showError(String text) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(text);

            log.error(text);
            alert.showAndWait();
        });
    }

    /**
     * Schedules a modal warning alert and records the message at warning level.
     *
     * @param text warning message displayed in the alert header
     */
    public static void showWarn(String text) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText(text);

            log.warn(text);
            alert.showAndWait();
        });
    }

    /**
     * Shows a synchronous warning confirmation on the JavaFX application thread.
     * Closing the dialog or selecting No declines the operation.
     *
     * @param text confirmation question displayed in the alert header
     * @return {@code true} only when the user selects Yes
     */
    public static boolean showConfirmation(String text) {
        Alert alert = new Alert(Alert.AlertType.WARNING, "", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Warning");
        alert.setHeaderText(text);
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }
}
