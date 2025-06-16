package com.github.exadmin.sourcesscanner.fxui.helpers;

import javafx.beans.property.Property;
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

    public enum CHOOSER_TYPE {
        FILE, DIRECTORY
    }

    private final Stage primaryStage;

    public ChooserBuilder(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public HBox buildChooserBox(String labelText, Property<String> bindProperty, String btnText, CHOOSER_TYPE type) {
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

            bindProperty.addListener((bean, oldValue, newValue) -> {
                Path newPath = Paths.get(newValue);
                File newFile = newPath.toFile();

                if (type == CHOOSER_TYPE.FILE) {
                    if (newFile.exists() && newFile.isFile()) {
                        File initFolder = newFile.getParentFile();
                        if (initFolder.exists() && initFolder.isDirectory()) fileChooser.setInitialDirectory(initFolder);
                    }
                }

                if (type == CHOOSER_TYPE.DIRECTORY) {
                    if (newFile.exists() && newFile.isDirectory()) dirChooser.setInitialDirectory(newFile);
                }
            });

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
