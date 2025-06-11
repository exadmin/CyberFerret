package com.github.exadmin.sourcesscanner.fxui.helpers;

import javafx.scene.control.Alert;

public class AlertBuilder {

    public static void showInfo(String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(text);
        alert.showAndWait();
    }

    public static void showError(String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(text);
        alert.showAndWait();
    }
}
