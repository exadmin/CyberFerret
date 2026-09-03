package com.github.exadmin.cyberferret.fxui.helpers;

import com.github.exadmin.cyberferret.fxui.FxConstants;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChooserBuilder {

    public enum CHOOSER_TYPE {
        FILE, DIRECTORY, EXECUTABLE
    }

    private final Stage primaryStage;

    public ChooserBuilder(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Builds a chooser row that always allows the native chooser to open.
     *
     * @param labelText label displayed beside the chooser field
     * @param bindProperty property bound to the selected path
     * @param btnText chooser button text
     * @param type kind of filesystem entry to select
     * @return configured chooser row
     */
    public HBox buildChooserBox(String labelText, Property<String> bindProperty, String btnText, CHOOSER_TYPE type) {
        return buildChooserBox(labelText, bindProperty, btnText, type, () -> true, ignored -> {
        });
    }

    /**
     * Builds a chooser row with hooks for opening confirmation and completed selection handling.
     * The selection callback runs only after an existing entry is selected.
     *
     * @param labelText label displayed beside the chooser field
     * @param bindProperty property bound to the selected path
     * @param btnText chooser button text
     * @param type kind of filesystem entry to select
     * @param beforeOpen callback that must approve opening the native chooser
     * @param onSelection callback invoked after the bound path changes through the chooser
     * @return configured chooser row
     */
    public HBox buildChooserBox(
            String labelText,
            Property<String> bindProperty,
            String btnText,
            CHOOSER_TYPE type,
            BooleanSupplier beforeOpen,
            Consumer<File> onSelection) {
        HBox hBox = new HBox();
        hBox.setSpacing(8);
        {
            Label label = new Label(labelText);
            label.setMinWidth(FxConstants.DEFAULT_LABEL_WIDTH);
            label.setAlignment(Pos.CENTER_LEFT);

            TextField textField = new TextField();
            textField.textProperty().bindBidirectional(bindProperty);
            HBox.setHgrow(textField, Priority.ALWAYS);

            Button btnOpen = new Button(btnText);
            btnOpen.setPrefWidth(FxConstants.DEFAULT_BUTTON_WIDTH);

            hBox.getChildren().addAll(label, textField, btnOpen);


            ObjectProperty<File> verifiedInitDirectory = new SimpleObjectProperty<>();

            // process init-directory for file-chooser
            bindProperty.addListener((bean, oldValue, newValue) -> {
                try {
                    Path newPath = Paths.get(newValue == null ? "" : newValue);
                    File newFile = newPath.toFile();

                    File initDir = initialDirectory(newValue);

                    // check init directory for the file/folder-chooser
                    if (initDir != null) {
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
                File file = chooseAfterConfirmation(beforeOpen, () -> {
                    if (type == CHOOSER_TYPE.FILE || type == CHOOSER_TYPE.EXECUTABLE) {
                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setInitialDirectory(verifiedInitDirectory.getValue());
                        if (type == CHOOSER_TYPE.EXECUTABLE) {
                            fileChooser.getExtensionFilters().add(
                                    new FileChooser.ExtensionFilter("Executable files (*.exe)", "*.exe"));
                        }
                        return fileChooser.showOpenDialog(primaryStage);
                    }

                    if (type == CHOOSER_TYPE.DIRECTORY) {
                        DirectoryChooser directoryChooser = new DirectoryChooser();
                        directoryChooser.setInitialDirectory(verifiedInitDirectory.getValue());
                        return directoryChooser.showDialog(primaryStage);
                    }
                    return null;
                });

                if (file != null && file.exists()) {
                    bindProperty.setValue(file.toString());
                    onSelection.accept(file);
                }
            });
        }

        return hBox;
    }

    /**
     * Opens a chooser only when its confirmation callback approves the operation.
     *
     * @param beforeOpen callback that approves opening the chooser
     * @param chooser native chooser operation
     * @return selected file, or {@code null} when opening is declined or selection is canceled
     */
    static File chooseAfterConfirmation(BooleanSupplier beforeOpen, Supplier<File> chooser) {
        return beforeOpen.getAsBoolean() ? chooser.get() : null;
    }

    static File initialDirectory(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        try {
            File configuredFile = Paths.get(configuredPath).toFile();
            if (!configuredFile.exists()) {
                return null;
            }
            File directory = configuredFile.isFile() ? configuredFile.getParentFile() : configuredFile;
            return directory != null && directory.isDirectory() ? directory : null;
        } catch (InvalidPathException ex) {
            return null;
        }
    }
}
