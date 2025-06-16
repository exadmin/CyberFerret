package com.github.exadmin.sourcesscanner.fxui.helpers;

import javafx.application.Platform;
import javafx.scene.control.Alert;
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
}
