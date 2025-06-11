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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeTableCell;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager.DICTIONARY;
import static com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager.DIR_TO_SCAN;

public class SceneBuilder {
    private static final Logger log = LoggerFactory.getLogger(SceneBuilder.class);

    private PersistentPropertiesManager appProperties;
    private Stage primaryStage;
    private FoundItemsContainer foundItemsContainer;
    private TreeItem<FoundPathItem> selectedItem;

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

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText("???");

            Button btnMark = new Button("Mark as ignored");
            btnMark.setOnAction(event -> {
                if (selectedItem == null) {
                    log.info("No items are selected to be marked as ignored!");
                    alert.setHeaderText("No items are selected to be marked as ignored");
                    alert.showAndWait();
                }
            });

            hBox.getChildren().add(btnLoadSigs);
            hBox.getChildren().add(btnRun);
            hBox.getChildren().add(btnMark);
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


        TreeTableColumn<FoundPathItem, String> colVisualName = new TreeTableColumn<>("Path name");
        TreeTableColumn<FoundPathItem, Boolean> colIgnore = new TreeTableColumn<>("To be ignored");
        TreeTableColumn<FoundPathItem, Long> colLine = new TreeTableColumn<>("Line #");
        TreeTableColumn<FoundPathItem, String> colText = new TreeTableColumn<>("Found Text");

        colVisualName.setCellValueFactory(new TreeItemPropertyValueFactory<>("visualName"));
        colIgnore.setCellValueFactory(new TreeItemPropertyValueFactory<>("signatureId"));
        colLine.setCellValueFactory(new TreeItemPropertyValueFactory<>("lineNumber"));
        colText.setCellValueFactory(new TreeItemPropertyValueFactory<>("text"));

        colIgnore.setCellFactory(p -> {
            CheckBoxTreeTableCell<FoundPathItem,Boolean> cell = new CheckBoxTreeTableCell<>();
            cell.setAlignment(Pos.CENTER);
            return cell;
        });


        // disable sorting - temporary
        colVisualName.setSortable(false);
        colVisualName.setEditable(false);
        colLine.setSortable(false);
        colLine.setEditable(false);
        colIgnore.setSortable(false);
        colIgnore.setEditable(false);
        colText.setEditable(false);
        colText.setSortable(false);

        colVisualName.prefWidthProperty().setValue(200d);

        TreeTableView<FoundPathItem> ttView = new TreeTableView<>();
        ttView.getColumns().add(colVisualName);
        ttView.getColumns().add(colIgnore);
        ttView.getColumns().add(colLine);
        ttView.getColumns().add(colText);


        ttView.setEditable(false);
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

            // automatically expand all parents for the node with signatures inside
            if (newItem.getType() == ItemType.SIGNATURE) {
                TreeItem<FoundPathItem> tItem = parentTreeItem;
                while (tItem != null) {
                    tItem.setExpanded(true);
                    tItem = tItem.getParent();
                }
            }

            // do sort
            parentTreeItem.getChildren().sort((item1, item2) -> {
                FoundPathItem fItem1 = item1.getValue();
                FoundPathItem fItem2 = item2.getValue();

                return fItem1.getType().getSortOrder() - fItem2.getType().getSortOrder();
            });

            map.put(newItem, newTreeItem);
        });

        ttView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<FoundPathItem>>() {
            @Override
            public void changed(ObservableValue<? extends TreeItem<FoundPathItem>> observableValue, TreeItem<FoundPathItem> oldItem, TreeItem<FoundPathItem> newItem) {
                selectedItem = newItem;
            }
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
