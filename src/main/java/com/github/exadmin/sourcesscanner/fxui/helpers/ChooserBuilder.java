package com.github.exadmin.sourcesscanner.fxui.helpers;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
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
import java.nio.file.InvalidPathException;
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


            ObjectProperty<File> verifiedInitDirectory = new SimpleObjectProperty<>();

            // process init-directory for file-chooser
            bindProperty.addListener((bean, oldValue, newValue) -> {
                try {
                    Path newPath = Paths.get(newValue);
                    File newFile = newPath.toFile();

                    File initDir = newFile.isFile() ? newFile.getParentFile() : newFile;
                    if (initDir.isFile()) initDir = initDir.getParentFile();

                    // check init directory for the file/folder-chooser
                    if (initDir.exists() && initDir.isDirectory()) {
                        verifiedInitDirectory.setValue(initDir);
                    }

                    // check selected value is correct
                    textField.setStyle(newFile.exists() ? "" : "-fx-background-color: #ffe6e6");
                } catch (InvalidPathException ex) {
                    textField.setStyle("-fx-background-color: #ffe6e6");
                }
            });

            // todo: remove wa which triggers listener
            bindProperty.setValue(bindProperty.getValue() + " ");
            bindProperty.setValue(bindProperty.getValue().trim());

            btnOpen.setOnAction(e -> {
                File file = null;
                if (type == CHOOSER_TYPE.FILE) {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setInitialDirectory(verifiedInitDirectory.getValue());
                    file = fileChooser.showOpenDialog(primaryStage);
                }

                if (type == CHOOSER_TYPE.DIRECTORY) {
                    DirectoryChooser directoryChooser = new DirectoryChooser();
                    directoryChooser.setInitialDirectory(verifiedInitDirectory.getValue());
                    file = directoryChooser.showDialog(primaryStage);
                }

                if (file != null && file.exists()) {
                    bindProperty.setValue(file.toString());
                }
            });
        }

        return hBox;
    }
}
