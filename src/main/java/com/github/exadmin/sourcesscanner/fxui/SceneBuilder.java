package com.github.exadmin.sourcesscanner.fxui;

import com.github.exadmin.sourcesscanner.async.RunnableLogger;
import com.github.exadmin.sourcesscanner.async.RunnableScanner;
import com.github.exadmin.sourcesscanner.fxui.helpers.ChooserBuilder;
import com.github.exadmin.sourcesscanner.model.FoundItem;
import com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

import static com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager.DICTIONARY;
import static com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager.DIR_TO_SCAN;

public class SceneBuilder {
    private static final int LABEL_MIN_WIDTH = 80;
    private static final int OPEN_FILE_BTN_WIDTH = 60;
    private static final Logger log = LoggerFactory.getLogger(SceneBuilder.class);

    private PersistentPropertiesManager appProperties;
    private Stage primaryStage;

    public SceneBuilder(PersistentPropertiesManager appProperties, Stage primaryStage) {
        this.appProperties = appProperties;
        this.primaryStage = primaryStage;
    }

    public Scene buildScene() {
        TabPane tabPane = new TabPane();

        // create tabs
        Tab tabAnalyzer = createAnalyzerTab(tabPane);
        tabPane.getTabs().add(tabAnalyzer);

        VBox vBox = new VBox(tabPane);
        Scene scene = new Scene(vBox);

        return scene;
    }

    protected Tab createAnalyzerTab(TabPane tabPane) {
        Tab tab = new Tab("Signatures Analyzer");
        tab.setClosable(false);

        TitledPane tpSettings = createSettingsGroup();
        TitledPane tpExplorer = createExplorerGroup(tabPane);
        TitledPane tpLogs = createLogsPane();

        // bPane.setStyle("-fx-background-color: red;");

        BorderPane bpRoot = new BorderPane();
        Pane wrapper = new StackPane(bpRoot);
        tab.setContent(wrapper);
        bpRoot.prefWidthProperty().bind(wrapper.widthProperty());
        bpRoot.prefHeightProperty().bind(wrapper.heightProperty());

        bpRoot.setTop(tpSettings);
        bpRoot.setCenter(tpExplorer);
        bpRoot.setBottom(tpLogs);

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
            btnRun.setOnAction(actionEvent -> {
                log.debug("Start button is pressed, where sig-file = {}, dir-to-scan = {}", DICTIONARY.getValue(), DIR_TO_SCAN.getValue());
                Runnable runnable = new RunnableScanner(DICTIONARY.getValue(), DIR_TO_SCAN.getValue());
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                thread.start();
            });

            hBox.getChildren().add(btnRun);

            // Test buttoon
            Button btnTest = new Button("Test");
            btnTest.setOnAction(event -> {
                log.debug("Hello!");
            });
            hBox.getChildren().add(btnTest);
        }

        return tpSettings;
    }

    private TitledPane createExplorerGroup(TabPane tabPane) {
        TitledPane tpExplorer = new TitledPane();
        tpExplorer.setText("Explorer");
        tpExplorer.setCollapsible(false);

        // explorer treetableview




        VBox vbox = new VBox();
        tpExplorer.setContent(vbox);

        TreeTableView<FoundItem> ttView = new TreeTableView<>();
        TreeTableColumn<FoundItem, String> colVisualName = new TreeTableColumn<>("Path name");
        TreeTableColumn<FoundItem, Boolean> colIsDir = new TreeTableColumn<>("Is Dir?");
        TreeTableColumn<FoundItem, Long> colPlace = new TreeTableColumn<>("Place");
        TreeTableColumn<FoundItem, String> colSigId = new TreeTableColumn<>("Signature Id");

        colVisualName.setCellValueFactory(new TreeItemPropertyValueFactory<>("visualName"));
        colIsDir.setCellValueFactory(new TreeItemPropertyValueFactory<>("isDirectory"));
        colPlace.setCellValueFactory(new TreeItemPropertyValueFactory<>("startPlace")); // todo: custom factory
        colSigId.setCellValueFactory(new TreeItemPropertyValueFactory<>("signatureId"));
        ttView.getColumns().add(colVisualName);
        ttView.getColumns().add(colIsDir);
        ttView.getColumns().add(colPlace);
        ttView.getColumns().add(colSigId);

        ttView.setShowRoot(true);
        ttView.setMinHeight(320);

        FoundItem modelItem = new FoundItem(Paths.get(""));
        modelItem.setVisualName("Files");
        modelItem.setIsDirectory(true);
        modelItem.setStartPlace(1000);
        modelItem.setEndPlace(1100);
        modelItem.setSignatureId("12323");

        TreeItem<FoundItem> rootTreeItem = new TreeItem<>(modelItem);
        rootTreeItem.setExpanded(true);
        ttView.setRoot(rootTreeItem);

        for (int i=0; i<50; i++) {
            FoundItem childModelItem = new FoundItem(Paths.get(""));
            childModelItem.setVisualName("Child " +i);

            TreeItem<FoundItem> childTreeItem = new TreeItem<>(childModelItem);
            rootTreeItem.getChildren().add(childTreeItem);
        }

        vbox.getChildren().add(ttView);
        vbox.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);
        ttView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(vbox, Priority.ALWAYS);
        VBox.setVgrow(ttView, Priority.ALWAYS);


        return tpExplorer;
    }


    private TitledPane createLogsPane() {
        TitledPane tpLogs = new TitledPane();
        tpLogs.setText("Console");
        tpLogs.setCollapsible(false);

        BorderPane bpRoot = new BorderPane();
        tpLogs.setContent(bpRoot);

        TextArea taLogs = new TextArea();
        bpRoot.setCenter(taLogs);
        taLogs.setEditable(false);

        Runnable runnable = new RunnableLogger(taLogs);
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.start();

        return tpLogs;
    }
}
