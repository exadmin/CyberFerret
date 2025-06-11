package com.github.exadmin.sourcesscanner.fxui;

import com.github.exadmin.sourcesscanner.async.RunnableLogger;
import com.github.exadmin.sourcesscanner.async.RunnableScanner;
import com.github.exadmin.sourcesscanner.async.RunnableSigsLoader;
import com.github.exadmin.sourcesscanner.fxui.helpers.ChooserBuilder;
import com.github.exadmin.sourcesscanner.model.FoundItemsContainer;
import com.github.exadmin.sourcesscanner.model.FoundPathItem;
import com.github.exadmin.sourcesscanner.model.ItemType;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager.DICTIONARY;
import static com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager.DIR_TO_SCAN;

public class SceneBuilder {
    private static final int LABEL_MIN_WIDTH = 80;
    private static final int OPEN_FILE_BTN_WIDTH = 60;
    private static final Logger log = LoggerFactory.getLogger(SceneBuilder.class);

    private PersistentPropertiesManager appProperties;
    private Stage primaryStage;
    private FoundItemsContainer foundItemsContainer;

    public SceneBuilder(PersistentPropertiesManager appProperties, Stage primaryStage) {
        this.appProperties = appProperties;
        this.primaryStage = primaryStage;
        this.foundItemsContainer = new FoundItemsContainer();
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

            Button btnRun = new Button("Start Scanning");
            final RunnableScanner runnableScanner = new RunnableScanner();
            runnableScanner.setBeforeStart(() -> btnRun.setDisable(true));
            runnableScanner.setAfterFinished(() -> btnRun.setDisable(false));

            btnRun.setOnAction(actionEvent -> {
                log.debug("Start button is pressed, where sig-file = {}, dir-to-scan = {}", DICTIONARY.getValue(), DIR_TO_SCAN.getValue());

                runnableScanner.setSignaturesFile(DICTIONARY.getValue());
                runnableScanner.setDirToScan(DIR_TO_SCAN.getValue());
                runnableScanner.setFoundItemsContainer(foundItemsContainer);
                runnableScanner.startNow();
            });

            // Load signatures button
            Button btnLoadSigs = new Button("Load Signatures");
            final RunnableSigsLoader runnableSigsLoader = new RunnableSigsLoader();
            runnableSigsLoader.setBeforeStart(() -> btnLoadSigs.setDisable(true));
            runnableSigsLoader.setAfterFinished(() -> {
                runnableScanner.setSignaturesMap(runnableSigsLoader.getRegExpMap());
                btnLoadSigs.setDisable(false);
            });

            btnLoadSigs.setOnAction(event -> {
                if (DICTIONARY.getValue() != null && !DICTIONARY.getValue().isEmpty()) {
                    Path sigsPath = Paths.get(DICTIONARY.getValue());

                    runnableSigsLoader.setFileToLoad(sigsPath);
                    runnableSigsLoader.startNow();
                } else {
                    log.warn("Signatures file is not selected. Please select it first.");
                }
            });

            hBox.getChildren().add(btnLoadSigs);
            hBox.getChildren().add(btnRun);
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

        TreeTableView<FoundPathItem> ttView = new TreeTableView<>();
        TreeTableColumn<FoundPathItem, String> colVisualName = new TreeTableColumn<>("Path name");
        TreeTableColumn<FoundPathItem, Boolean> colIsDir = new TreeTableColumn<>("Is Dir?");
        TreeTableColumn<FoundPathItem, Long> colPlace = new TreeTableColumn<>("Place");
        TreeTableColumn<FoundPathItem, String> colSigId = new TreeTableColumn<>("Signature Id");

        colVisualName.setCellValueFactory(new TreeItemPropertyValueFactory<>("visualName"));
        colIsDir.setCellValueFactory(new TreeItemPropertyValueFactory<>("isDirectory"));
        colPlace.setCellValueFactory(new TreeItemPropertyValueFactory<>("startPlace")); // todo: custom factory
        colSigId.setCellValueFactory(new TreeItemPropertyValueFactory<>("signatureId"));
        ttView.getColumns().add(colVisualName);
        ttView.getColumns().add(colIsDir);
        ttView.getColumns().add(colPlace);
        ttView.getColumns().add(colSigId);

        // disable sorting - temporary
        colVisualName.setSortable(false);
        colIsDir.setSortable(false);
        colPlace.setSortable(false);
        colSigId.setSortable(false);

        colVisualName.prefWidthProperty().setValue(200d);

        ttView.setShowRoot(false);
        ttView.setMinHeight(320);

        final Map<FoundPathItem, TreeItem<FoundPathItem>> map = new HashMap<>();

        final FoundPathItem fakeItem = new FoundPathItem(Paths.get(""), ItemType.DIRECTORY, null);
        final TreeItem<FoundPathItem> rootTreeItem = new TreeItem<>(fakeItem);

        foundItemsContainer.setOnAddNewItemListener(newItem -> {
            TreeItem<FoundPathItem> newTreeItem = new TreeItem<>(newItem);

            TreeItem<FoundPathItem> parentTreeItem = map.get(newItem.getParent());
            if (parentTreeItem == null) parentTreeItem = rootTreeItem;

            parentTreeItem.getChildren().add(newTreeItem);

            // do sort
            parentTreeItem.getChildren().sort((item1, item2) -> {
                FoundPathItem fItem1 = item1.getValue();
                FoundPathItem fItem2 = item2.getValue();

                return fItem1.getType().getSortOrder() - fItem2.getType().getSortOrder();
            });

            map.put(newItem, newTreeItem);
        });


        ttView.setRoot(rootTreeItem);

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
