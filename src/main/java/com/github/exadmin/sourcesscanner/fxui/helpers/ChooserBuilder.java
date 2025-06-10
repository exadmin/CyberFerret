package com.github.exadmin.sourcesscanner.fxui.helpers;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ChooserBuilder {
    private static final int LABEL_DEFAULT_WIDTH = 80;
    private static final int OPEN_BTN_DEFAULT_WIDTH = 80;

    public static enum CHOOSER_TYPE {
        FILE, DIRECTORY;
    }

    private Stage primaryStage;

    public ChooserBuilder(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public HBox buildChooserBox(String labelText, StringProperty bindProperty, String btnText, CHOOSER_TYPE type) {
        HBox hBox = new HBox();
        hBox.setSpacing(8);
        {
            Label label = new Label(labelText);
            label.setMinWidth(LABEL_DEFAULT_WIDTH);
            label.setAlignment(Pos.CENTER_LEFT);

            TextField textField = new TextField();
            textField.textProperty().bindBidirectional(bindProperty);
            HBox.setHgrow(textField, Priority.ALWAYS);

            Button btnOpen = new Button(btnText);
            btnOpen.setMinWidth(OPEN_BTN_DEFAULT_WIDTH);

            hBox.getChildren().addAll(label, textField, btnOpen);

            // setup file-chooser
            FileChooser fileChooser = type == CHOOSER_TYPE.FILE ? new FileChooser() : null;
            DirectoryChooser dirChooser = type == CHOOSER_TYPE.DIRECTORY ? new DirectoryChooser() : null;

            if (bindProperty.getValue() != null) {
                Path storedPath = Paths.get(bindProperty.getValue());
                File storedFile = storedPath.toFile();
                if (storedFile.exists() && storedFile.isFile()) {
                    File parentFolder = storedFile.getParentFile();
                    if (parentFolder.exists() && parentFolder.isDirectory()) {
                        if (fileChooser != null) fileChooser.setInitialDirectory(parentFolder);
                        if (dirChooser != null) dirChooser.setInitialDirectory(parentFolder);
                    }
                }
            }

            btnOpen.setOnAction(e -> {
                File file = type == CHOOSER_TYPE.FILE ? fileChooser.showOpenDialog(primaryStage) : dirChooser.showDialog(primaryStage);

                if (file != null && file.exists()) {
                    bindProperty.setValue(file.toString());
                }
            });
        }

        return hBox;
    }
}
