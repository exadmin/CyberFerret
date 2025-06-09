package com.github.exadmin.sourcesscanner.fxui;

import com.github.exadmin.sourcesscanner.context.AppAbstractProperty;
import com.github.exadmin.sourcesscanner.context.AppProperties;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SceneBuilder {
    private static final int LABEL_MIN_WIDTH        = 80;
    private static final int OPEN_FILE_BTN_WIDTH    =  60;

    private AppProperties appProperties;
    private Stage primaryStage;

    public SceneBuilder(AppProperties appProperties, Stage primaryStage) {
        this.appProperties = appProperties;
        this.primaryStage = primaryStage;
    }

    public Scene buildScene() {
        TabPane tabPane = new TabPane();

        // create tabs
        Tab tabAnalyzer = createAnalyzerTab();
        tabPane.getTabs().add(tabAnalyzer);

        VBox vBox = new VBox(tabPane);
        Scene scene = new Scene(vBox);

        return scene;
    }

    protected Tab createAnalyzerTab() {
        Tab tab = new Tab("Signatures Analyzer");
        tab.setClosable(false);

        TitledPane tpSettings = createSettingsGroup();
        TitledPane tpExplorer = createExplorerGroup();

        VBox vBox = new VBox();
        tab.setContent(vBox);
        vBox.setPadding(new Insets(4));
        vBox.setSpacing(8);
        vBox.getChildren().addAll(tpSettings, tpExplorer);

        return tab;
    }

    protected TitledPane createSettingsGroup() {
        TitledPane tpSettings = new TitledPane();
        tpSettings.setCollapsible(false);
        tpSettings.setText("Settings");

        VBox vBoxRoot = new VBox();
        tpSettings.setContent(vBoxRoot);
        vBoxRoot.setSpacing(8);

        // Dictionary
        {
            HBox hBox = new HBox();
            vBoxRoot.getChildren().add(hBox);
            hBox.setSpacing(8);
            {
                Label lbDictionary = new Label("Dictionary");
                lbDictionary.setMinWidth(LABEL_MIN_WIDTH);
                lbDictionary.setAlignment(Pos.CENTER_LEFT);

                TextField tfDictionary = new TextField("");
                HBox.setHgrow(tfDictionary, Priority.ALWAYS);

                Button btnOpen = new Button("...");
                btnOpen.setMinWidth(OPEN_FILE_BTN_WIDTH);

                hBox.getChildren().addAll(lbDictionary, tfDictionary, btnOpen);

                FileChooser fileChooser = new FileChooser();
                btnOpen.setOnAction(e -> {
                    File file = fileChooser.showOpenDialog(primaryStage);
                    if (file != null && file.exists()) {
                        tfDictionary.setText(file.toString());
                        appProperties.setValue(AppAbstractProperty.DICTIONARY, file.toString());
                    }
                });

                // init tfDictionary with value if exist
                String storedValue = appProperties.getValue(AppAbstractProperty.DICTIONARY);
                tfDictionary.setText(storedValue);

                Path storedPath = Paths.get(storedValue);
                File storedFile = storedPath.toFile();
                if (storedFile.exists() && storedFile.isFile()) {
                    File parentFolder = storedFile.getParentFile();
                    if (parentFolder.exists() && parentFolder.isDirectory()) {
                        fileChooser.setInitialDirectory(parentFolder);
                    }
                }
            }
        }

        // Folder to scane
        {
            HBox hBox = new HBox();
            vBoxRoot.getChildren().add(hBox);
            hBox.setSpacing(8);
            {
                Label lbDir = new Label("Root dir");
                lbDir.setAlignment(Pos.CENTER_LEFT);
                lbDir.setMinWidth(LABEL_MIN_WIDTH);

                TextField tfDirToScan = new TextField("");
                HBox.setHgrow(tfDirToScan, Priority.ALWAYS);

                Button btnOpen = new Button("...");
                btnOpen.setMinWidth(OPEN_FILE_BTN_WIDTH);

                hBox.getChildren().addAll(lbDir, tfDirToScan, btnOpen);

                // init tfDictionary with value if exist
                String storedValue = appProperties.getValue(AppAbstractProperty.DIR_TO_SCAN);
                tfDirToScan.setText(storedValue);
            }
        }

        // control buttons
        {
            HBox hBox = new HBox();
            vBoxRoot.getChildren().add(hBox);
            hBox.setSpacing(8);

            Button btnRun = new Button("Start");

            hBox.getChildren().add(btnRun);
        }

        return tpSettings;
    }

    private TitledPane createExplorerGroup() {
        TitledPane tpExplorer = new TitledPane();
        tpExplorer.setText("Explorer");
        tpExplorer.setCollapsible(false);

        VBox vBox = new VBox();
        tpExplorer.setContent(vBox);
        vBox.setSpacing(0);
        vBox.setPadding(new Insets(2));
        vBox.setFillWidth(true);

        TreeTableView ttView = new TreeTableView();
        vBox.getChildren().add(ttView);


        return tpExplorer;

    }
}
