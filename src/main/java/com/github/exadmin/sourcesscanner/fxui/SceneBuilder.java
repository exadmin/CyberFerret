package com.github.exadmin.sourcesscanner.fxui;

import com.github.exadmin.sourcesscanner.context.PersistentPropertiesManager;
import com.github.exadmin.sourcesscanner.fxui.helpers.ChooserBuilder;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import static com.github.exadmin.sourcesscanner.context.PersistentPropertiesManager.DICTIONARY;
import static com.github.exadmin.sourcesscanner.context.PersistentPropertiesManager.DIR_TO_SCAN;

public class SceneBuilder {
    private static final int LABEL_MIN_WIDTH        = 80;
    private static final int OPEN_FILE_BTN_WIDTH    =  60;

    private PersistentPropertiesManager appProperties;
    private Stage primaryStage;

    public SceneBuilder(PersistentPropertiesManager appProperties, Stage primaryStage) {
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

        ChooserBuilder chooserBuilder = new ChooserBuilder(primaryStage);

        // Dictionary
        {
            StringProperty sigFileProperty = new SimpleStringProperty(DICTIONARY.getValue());
            sigFileProperty.addListener((value, oldValue, newValue) -> DICTIONARY.parseValue(newValue));

            HBox hBox = chooserBuilder.buildChooserBox("Signatures registry", sigFileProperty, "...", ChooserBuilder.CHOOSER_TYPE.FILE);
            vBoxRoot.getChildren().add(hBox);
        }

        // Folder to scane
        {
            StringProperty dirToScanProperty = new SimpleStringProperty(DIR_TO_SCAN.getValue());
            dirToScanProperty.addListener((prop, oldValue, newValue) -> DIR_TO_SCAN.parseValue(newValue));
            HBox hBox = chooserBuilder.buildChooserBox("Directory to scan", dirToScanProperty, "...", ChooserBuilder.CHOOSER_TYPE.DIRECTORY);
            vBoxRoot.getChildren().add(hBox);
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
