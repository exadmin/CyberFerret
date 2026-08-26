package com.github.exadmin.cyberferret.fxui;

import com.github.exadmin.cyberferret.AppConstants;
import com.github.exadmin.cyberferret.async.RunnableLogger;
import com.github.exadmin.cyberferret.cfcli.CfCliExecutable;
import com.github.exadmin.cyberferret.cfcli.CfCliScanner;
import com.github.exadmin.cyberferret.cfcli.CfCliTreeAssembler;
import com.github.exadmin.cyberferret.exclude.Excluder;
import com.github.exadmin.cyberferret.fxui.helpers.AlertBuilder;
import com.github.exadmin.cyberferret.fxui.helpers.ChooserBuilder;
import com.github.exadmin.cyberferret.model.FoundFileItemListener;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import com.github.exadmin.cyberferret.utils.MiscUtils;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.exadmin.cyberferret.fxui.FxConstants.DEFAULT_BUTTON_WIDTH;
import static com.github.exadmin.cyberferret.fxui.FxConstants.DEFAULT_LABEL_WIDTH;
import static com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager.*;

public class SceneBuilder {
    static final String EXCLUDED_ROW_STYLE = "-fx-background-color: #f0e73a;";
    private static final Logger log = LoggerFactory.getLogger(SceneBuilder.class);

    private final Stage primaryStage;
    private final FoundItemsContainer foundItemsContainer;
    private final ObjectProperty<TreeItem<FoundPathItem>> selectedItemProperty = new SimpleObjectProperty<>();
    private final AtomicBoolean scannerRunning = new AtomicBoolean(false);

    public SceneBuilder(Stage primaryStage) {
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

        scene.getStylesheets().add(getClass().getResource("/fxstyles.css").toExternalForm());

        return scene;
    }

    protected Tab createAnalyzerTab(TabPane tabPane) {
        Tab tab = new Tab("Signatures Analyzer");
        tab.setClosable(false);

        TitledPane tpOnlineDictionary = createOnlineDictionaryPane();
        TitledPane tpRepository = createRepositoryGroup();
        TitledPane tpExplorer = createExplorerGroup(tabPane);
        TitledPane tpConsole = createLogsPane();

        VBox vBox = new VBox();
        tab.setContent(vBox);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(new VBox(tpExplorer), new VBox(tpConsole));

        vBox.getChildren().add(tpOnlineDictionary);
        vBox.getChildren().add(tpRepository);
        vBox.getChildren().add(splitPane);
        vBox.setSpacing(2);

        // make components fill all available space
        VBox.setVgrow(tpExplorer, Priority.ALWAYS);
        tpExplorer.setMaxHeight(Double.MAX_VALUE);

        VBox.setVgrow(splitPane, Priority.ALWAYS);
        splitPane.setMaxHeight(Double.MAX_VALUE);

        VBox.setVgrow(tpConsole, Priority.ALWAYS);
        tpConsole.setMaxHeight(Double.MAX_VALUE);

        VBox.setVgrow(tabPane, Priority.ALWAYS);

        return tab;
    }

    protected TitledPane createOnlineDictionaryPane() {
        TitledPane tpOnlineDictionary = new TitledPane();
        tpOnlineDictionary.setCollapsible(false);
        tpOnlineDictionary.setExpanded(true);
        tpOnlineDictionary.setText("Online Dictionary");

        VBox vBoxRoot = new VBox();
        tpOnlineDictionary.setContent(vBoxRoot);
        vBoxRoot.setSpacing(8);

        ChooserBuilder chooserBuilder = new ChooserBuilder(primaryStage);
        vBoxRoot.getChildren().add(chooserBuilder.buildChooserBox(
                "CyberFerret CLI executable",
                CF_CLI_PATH.getFxProperty(),
                "Select ...",
                ChooserBuilder.CHOOSER_TYPE.EXECUTABLE));

        String environmentPassword = System.getenv(AppConstants.SYS_ENV_VAR_PASSWORD);
        String strStatus = MiscUtils.isEmpty(environmentPassword) ? "not found ✖" : "found ✔";
        Label passwordLabel = new Label("Password in '" + AppConstants.SYS_ENV_VAR_PASSWORD + "' environment variable is " + strStatus);
        passwordLabel.setMinWidth(DEFAULT_LABEL_WIDTH);
        passwordLabel.setAlignment(Pos.CENTER_LEFT);
        HBox passwordRow = new HBox(8, passwordLabel);
        vBoxRoot.getChildren().add(passwordRow);

        return tpOnlineDictionary;
    }

    protected TitledPane createRepositoryGroup() {
        TitledPane tpSettings = new TitledPane();
        tpSettings.setCollapsible(false);
        tpSettings.setExpanded(true);
        tpSettings.setText("Repository");

        VBox vBoxRoot = new VBox();
        tpSettings.setContent(vBoxRoot);
        vBoxRoot.setSpacing(8);

        ChooserBuilder chooserBuilder = new ChooserBuilder(primaryStage);

        // Folder to scan
        {
            HBox hBox = chooserBuilder.buildChooserBox("Git repository to scan", DIR_TO_SCAN.getFxProperty(), "Select ...", ChooserBuilder.CHOOSER_TYPE.DIRECTORY);
            vBoxRoot.getChildren().add(hBox);
        }

        // control buttons
        {
            HBox hBox = new HBox();
            vBoxRoot.getChildren().add(hBox);
            hBox.setSpacing(8);

            Button btnRun = new Button("Start Scanning");
            btnRun.setPrefWidth(DEFAULT_BUTTON_WIDTH);

            btnRun.setOnAction(actionEvent -> {
                log.debug("Start button is pressed using dir-to-scan = {}", DIR_TO_SCAN.getValue());

                String selectedDirectory = DIR_TO_SCAN.getValue();
                if (selectedDirectory == null || selectedDirectory.isBlank()) {
                    AlertBuilder.showError("Select a repository directory before scanning.");
                    return;
                }
                Path scanRoot;
                try {
                    scanRoot = Paths.get(selectedDirectory).toAbsolutePath().normalize();
                } catch (RuntimeException ex) {
                    AlertBuilder.showError("Invalid repository directory: " + selectedDirectory);
                    return;
                }
                if (!Files.isDirectory(scanRoot)) {
                    AlertBuilder.showError("Repository directory does not exist: " + scanRoot);
                    return;
                }

                CfCliExecutable cliExecutable = new CfCliExecutable(CF_CLI_PATH.getValue());
                var executableError = cliExecutable.validationError();
                if (executableError.isPresent()) {
                    AlertBuilder.showError(executableError.get());
                    return;
                }

                if (!scannerRunning.compareAndSet(false, true)) {
                    log.warn("Scanning is already in progress");
                    return;
                }

                // drop previous scan result
                foundItemsContainer.clearAll();

                btnRun.setDisable(true);
                CfCliTreeAssembler assembler = new CfCliTreeAssembler(
                        scanRoot,
                        foundItemsContainer,
                        message -> log.warn("{}", message));
                CfCliScanner scanner = new CfCliScanner(
                        cliExecutable.command(),
                        scanRoot,
                        assembler::accept,
                        message -> log.info("{}", message),
                        message -> {
                            log.error("{}", message);
                            runOnFxThread(() -> AlertBuilder.showError(message));
                        },
                        () -> {
                            scannerRunning.set(false);
                            runOnFxThread(() -> btnRun.setDisable(false));
                        });
                Thread scannerThread = new Thread(scanner, "cyberferret-go-cli-scanner");
                scannerThread.setDaemon(true);
                scannerThread.start();
            });


            Button btnMark = new Button("Mark as ignored");
            btnMark.setPrefWidth(DEFAULT_BUTTON_WIDTH);
            btnMark.setOnAction(new MarkAsIgnoredEventHandler());

            selectedItemProperty.addListener((bean, olValue, newValue) -> {
                if (bean != null && bean.getValue() != null && bean.getValue().getValue() != null) {
                    log.info("Selected file path: {}", bean.getValue().getValue().getFilePath().toAbsolutePath());
                }
            });

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
        TreeTableColumn<FoundPathItem, String> colStatus = new TreeTableColumn<>("Status");
        TreeTableColumn<FoundPathItem, Long> colLine = new TreeTableColumn<>("Line #");
        TreeTableColumn<FoundPathItem, String> colDisplayText = new TreeTableColumn<>("Found Text");
        TreeTableColumn<FoundPathItem, String> colExactSignature = new TreeTableColumn<>("Exact Signature");

        colVisualName.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getVisualName()));
        colStatus.setCellValueFactory(param -> new ReadOnlyStringWrapper(statusFor(param.getValue().getValue())));
        colLine.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue().getLineNumber()));
        colDisplayText.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getDisplayText()));
        colExactSignature.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getFoundString()));

        // disable sorting - temporary
        colVisualName.setSortable(false);
        colVisualName.setEditable(false);
        colLine.setSortable(false);
        colLine.setEditable(false);
        colStatus.setSortable(false);
        colStatus.setEditable(false);
        colExactSignature.setSortable(false);
        colExactSignature.setEditable(false);
        colDisplayText.setEditable(false);
        colDisplayText.setSortable(false);

        TreeTableView<FoundPathItem> ttView = new TreeTableView<>();
        ttView.getColumns().add(colVisualName);
        ttView.getColumns().add(colStatus);
        ttView.getColumns().add(colLine);
        ttView.getColumns().add(colExactSignature);
        ttView.getColumns().add(colDisplayText);

        colVisualName.setPrefWidth(PATH_NAME_COLUMN_WIDTH.getValue().doubleValue());
        colStatus.setPrefWidth(STATUS_COLUMN_WIDTH.getValue().doubleValue());
        colLine.setPrefWidth(LINE_COLUMN_WIDTH.getValue().doubleValue());
        colExactSignature.setPrefWidth(EXACT_SIGNATURE_COLUMN_WIDTH.getValue().doubleValue());
        colDisplayText.setPrefWidth(FOUND_TEXT_COLUMN_WIDTH.getValue().doubleValue());

        Platform.runLater(() -> {
            colVisualName.setPrefWidth(PATH_NAME_COLUMN_WIDTH.getValue().doubleValue());
            colStatus.setPrefWidth(STATUS_COLUMN_WIDTH.getValue().doubleValue());
            colLine.setPrefWidth(LINE_COLUMN_WIDTH.getValue().doubleValue());
            colExactSignature.setPrefWidth(EXACT_SIGNATURE_COLUMN_WIDTH.getValue().doubleValue());
            colDisplayText.setPrefWidth(FOUND_TEXT_COLUMN_WIDTH.getValue().doubleValue());

            colVisualName.widthProperty().addListener(
                    (value, oldValue, newValue) -> PATH_NAME_COLUMN_WIDTH.parseValue(newValue));
            colStatus.widthProperty().addListener(
                    (value, oldValue, newValue) -> STATUS_COLUMN_WIDTH.parseValue(newValue));
            colLine.widthProperty().addListener(
                    (value, oldValue, newValue) -> LINE_COLUMN_WIDTH.parseValue(newValue));
            colExactSignature.widthProperty().addListener(
                    (value, oldValue, newValue) -> EXACT_SIGNATURE_COLUMN_WIDTH.parseValue(newValue));
            colDisplayText.widthProperty().addListener(
                    (value, oldValue, newValue) -> FOUND_TEXT_COLUMN_WIDTH.parseValue(newValue));
        });

        ttView.setEditable(false);
        ttView.setShowRoot(false);
        ttView.setMinHeight(320);

        ttView.setRowFactory(tv -> new TreeTableRow<>() {
            @Override
            protected void updateItem(FoundPathItem foundPathItem, boolean isSelected) {
                if (foundPathItem == null) {
                    setStyle("");
                    setContextMenu(null);
                } else {
                    if (!isSelected && foundPathItem.isIgnored()) {
                        setStyle(EXCLUDED_ROW_STYLE);
                    } else if (!isSelected && foundPathItem.isAllowedValue()) {
                        setStyle("-fx-background-color: #c1f7cf;");
                    } else if (!isSelected && foundPathItem.getFoundString() != null && !foundPathItem.getFoundString().isEmpty()) {
                        setStyle("-fx-background-color: #f2d0d0;");
                    } else {
                        setStyle("");
                    }

                    ContextMenu contextMenu = createContextMenu(foundPathItem);
                    setContextMenu(contextMenu);
                }
                super.updateItem(foundPathItem, isSelected);
            }

            private ContextMenu createContextMenu(FoundPathItem foundPathItem) {
                Path filePath = foundPathItem.getFilePath();
                File file = (filePath != null) ? filePath.toFile() : null;

                MenuItem openInEditor = new MenuItem("Open in editor");
                openInEditor.setOnAction(event -> {
                    try {
                        if (file != null && file.exists() && file.isFile()) {
                            Desktop.getDesktop().open(file);
                        }
                    } catch (Exception ex) {
                        log.error("Failed to open file {} in editor", filePath, ex);
                    }
                });

                MenuItem openInExplorer = new MenuItem("Open in explorer");
                openInExplorer.setOnAction(event -> {
                    try {
                        if (file != null && file.exists()) {
                            Desktop.getDesktop().open(file.getParentFile());
                        }
                    } catch (Exception ex) {
                        log.error("Failed to open folder of file {} in explorer", filePath, ex);
                    }
                });

                MenuItem copySignature = new MenuItem("Copy found signature");
                copySignature.setOnAction(event -> {
                    String signature = foundPathItem.getFoundString();
                    if (signature != null && !signature.isEmpty()) {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(signature);
                        clipboard.setContent(content);
                    }
                });

                MenuItem markAsIgnored = new MenuItem("Mark as ignored");
                markAsIgnored.setOnAction(new MarkAsIgnoredEventHandler());

                return new ContextMenu(openInEditor, openInExplorer, copySignature, markAsIgnored);
            }
        });

        final Map<FoundPathItem, TreeItem<FoundPathItem>> map = new HashMap<>();

        final FoundPathItem fakeItem = new FoundPathItem(Paths.get(""), ItemType.DIRECTORY, null);
        final TreeItem<FoundPathItem> rootTreeItem = new TreeItem<>(fakeItem);

        foundItemsContainer.setOnAddNewItemListener(new FoundFileItemListener() {
            @Override
            public void newItemAdded(FoundPathItem newItem) {
                newItemAdded(newItem, foundItemsContainer.getGeneration());
            }

            @Override
            public void newItemAdded(FoundPathItem newItem, long generation) {
                runOnFxThread(() -> {
                    if (generation != foundItemsContainer.getGeneration()) return;

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
            }

            @Override
            public void onClearAll() {
                onClearAll(foundItemsContainer.getGeneration());
            }

            @Override
            public void onClearAll(long generation) {
                runOnFxThread(() -> {
                    if (generation != foundItemsContainer.getGeneration()) return;

                    rootTreeItem.getChildren().clear();
                    map.clear();
                });
            }

            @Override
            public void itemUpdated(FoundPathItem item, long generation) {
                runOnFxThread(() -> {
                    if (generation != foundItemsContainer.getGeneration()) return;
                    ttView.refresh();
                });
            }
        });

        ttView.getSelectionModel().selectedItemProperty().addListener((bean, oldItem, newItem) -> {
            // cache selected item
            selectedItemProperty.setValue(newItem);
        });


        ttView.setRoot(rootTreeItem);

        vbox.getChildren().add(ttView);
        vbox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        ttView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(vbox, Priority.ALWAYS);
        VBox.setVgrow(ttView, Priority.ALWAYS);


        return tpExplorer;
    }

    static String statusFor(FoundPathItem item) {
        return switch (item.getType()) {
            case DIRECTORY -> "Folder";
            case FILE -> "File";
            case SIGNATURE -> {
                if (item.isAllowedValue()) yield "Allowed";
                if (item.isIgnored()) yield "Excluded";
                yield "Warning";
            }
        };
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

    private void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private class MarkAsIgnoredEventHandler implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent event) {
            if (selectedItemProperty.getValue() == null) {
                AlertBuilder.showInfo("No items are selected to be marked as ignored!");
            } else {
                FoundPathItem foundPathItem = selectedItemProperty.getValue().getValue();
                Path resultYaml = Excluder.markToExclude(foundPathItem, Paths.get(DIR_TO_SCAN.toString()));
                if (resultYaml == null) {
                    AlertBuilder.showError("Can't load existed exclusion configuration, please check logs and fix errors. If can't - then delete erroneous file.");
                } else {
                    log.info("Item {} was successfully {} as ignored, the result is stored into {}", foundPathItem, foundPathItem.isIgnored() ? "(+)marked" : "(-)unmarked", resultYaml);
                }
            }
        }
    }
}
