package com.github.exadmin.cyberferret.fxui;

import com.github.exadmin.cyberferret.AppConstants;
import com.github.exadmin.cyberferret.async.*;
import com.github.exadmin.cyberferret.exclude.Excluder;
import com.github.exadmin.cyberferret.fxui.helpers.AlertBuilder;
import com.github.exadmin.cyberferret.fxui.helpers.ChooserBuilder;
import com.github.exadmin.cyberferret.cfcli.CfCliScanner;
import com.github.exadmin.cyberferret.cfcli.CfCliExecutable;
import com.github.exadmin.cyberferret.cfcli.CfCliTreeAssembler;
import com.github.exadmin.cyberferret.model.FoundFileItemListener;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import com.github.exadmin.cyberferret.utils.FileUtils;
import com.github.exadmin.cyberferret.utils.PasswordBasedEncryption;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.ObjectProperty;
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
import javafx.scene.control.cell.CheckBoxTreeTableCell;
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
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.github.exadmin.cyberferret.fxui.FxConstants.*;
import static com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager.*;

public class SceneBuilder {
    private static final Logger log = LoggerFactory.getLogger(SceneBuilder.class);

    private final Stage primaryStage;
    private final FoundItemsContainer foundItemsContainer;
    private final ObjectProperty<TreeItem<FoundPathItem>> selectedItemProperty = new SimpleObjectProperty<>();
    private final AtomicBoolean dictionaryLoading = new AtomicBoolean(false);
    private final AtomicBoolean scannerRunning = new AtomicBoolean(false);
    private volatile Map<String, Pattern> signaturesMap = Map.of();
    private volatile Map<String, String> allowedSignaturesMap = Map.of();
    private volatile Map<String, List<String>> excludeExtMap = Map.of();

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

    public void loadDecryptedDictionaryIfExists() {
        Path sigsPath = Paths.get(AppConstants.DICTIONARY_FILE_PATH_DECRYPTED);
        File sigsFile = sigsPath.toFile();
        if (sigsFile.exists() && sigsFile.isFile()) {
            log.info("Loading decrypted dictionary from {}", sigsFile.getAbsolutePath());
            loadDecryptedDictionary();
        }
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

        Label passwordLabel = new Label("Password");
        passwordLabel.setMinWidth(DEFAULT_LABEL_WIDTH);
        passwordLabel.setAlignment(Pos.CENTER_LEFT);
        PasswordField passwordField = new PasswordField();
        passwordField.setEditable(false);
        String environmentPassword = System.getenv(AppConstants.SYS_ENV_VAR_PASSWORD);
        passwordField.setText(environmentPassword == null ? "" : environmentPassword);
        HBox passwordRow = new HBox(8, passwordLabel, passwordField);
        HBox.setHgrow(passwordField, Priority.ALWAYS);
        vBoxRoot.getChildren().add(passwordRow);

        ChooserBuilder chooserBuilder = new ChooserBuilder(primaryStage);
        vBoxRoot.getChildren().add(chooserBuilder.buildChooserBox(
                "CF CLI executable",
                CF_CLI_PATH.getFxProperty(),
                "Select ...",
                ChooserBuilder.CHOOSER_TYPE.EXECUTABLE));

        return tpOnlineDictionary;
    }

    protected TitledPane createOfflineDictionaryPane() {
        TitledPane tpSettings = new TitledPane();
        tpSettings.setCollapsible(true);
        tpSettings.setText("Offline Dictionary");

        VBox vBoxRoot = new VBox();
        tpSettings.setContent(vBoxRoot);
        vBoxRoot.setSpacing(8);

        ChooserBuilder chooserBuilder = new ChooserBuilder(primaryStage);

        // Dictionary
        {
            HBox hBox = chooserBuilder.buildChooserBox("Local dictionary", DICTIONARY.getFxProperty(), "Select file", ChooserBuilder.CHOOSER_TYPE.FILE);
            vBoxRoot.getChildren().add(hBox);

            // Load signatures button
            Button btnLoadSigs = new Button("Load it");
            hBox.getChildren().add(btnLoadSigs);
            btnLoadSigs.setPrefWidth(DEFAULT_BUTTON_WIDTH);

            btnLoadSigs.setOnAction(event -> {
                if (DICTIONARY.getValue() != null && !DICTIONARY.getValue().isEmpty()) {
                    Path sigsPath = Paths.get(DICTIONARY.getValue());
                    loadSignatures(sigsPath, btnLoadSigs);
                } else {
                    log.warn("Signatures file is not selected. Please select it first.");
                }
            });
        }

        return tpSettings;
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
                log.debug("Start button is pressed using dictionary {}, dir-to-scan = {}", DICTIONARY.getValue(), DIR_TO_SCAN.getValue());

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
                            runOnFxThread(() -> showScannerMessage(FxCallback.FxCallbackType.ERROR, message));
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
        TreeTableColumn<FoundPathItem, Boolean> colIgnore = new TreeTableColumn<>("To be ignored");
        TreeTableColumn<FoundPathItem, Boolean> colAllowed = new TreeTableColumn<>("Allowed");
        TreeTableColumn<FoundPathItem, Long> colLine = new TreeTableColumn<>("Line #");
        TreeTableColumn<FoundPathItem, String> colDisplayText = new TreeTableColumn<>("Found Text");
        TreeTableColumn<FoundPathItem, String> colExactSignature = new TreeTableColumn<>("Exact Signature");

        colVisualName.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getVisualName()));
        colIgnore.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue().isIgnored()));
        colAllowed.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue().isAllowedValue()));
        colLine.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue().getLineNumber()));
        colDisplayText.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getDisplayText()));
        colExactSignature.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getFoundString()));

        colIgnore.setCellFactory(p -> {
            CheckBoxTreeTableCell<FoundPathItem, Boolean> cell = new CheckBoxTreeTableCell<>();
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
        colExactSignature.setSortable(false);
        colExactSignature.setEditable(false);
        colDisplayText.setEditable(false);
        colDisplayText.setSortable(false);
        colAllowed.setEditable(false);
        colAllowed.setSortable(false);

        TreeTableView<FoundPathItem> ttView = new TreeTableView<>();
        ttView.getColumns().add(colVisualName);
        ttView.getColumns().add(colIgnore);
        ttView.getColumns().add(colAllowed);
        ttView.getColumns().add(colLine);
        ttView.getColumns().add(colExactSignature);
        ttView.getColumns().add(colDisplayText);

        colVisualName.setPrefWidth(PATH_NAME_COLUMN_WIDTH.getValue().doubleValue());
        colIgnore.setPrefWidth(IGNORE_COLUMN_WIDTH.getValue().doubleValue());
        colAllowed.setPrefWidth(ALLOWED_COLUMN_WIDTH.getValue().doubleValue());
        colLine.setPrefWidth(LINE_COLUMN_WIDTH.getValue().doubleValue());
        colExactSignature.setPrefWidth(EXACT_SIGNATURE_COLUMN_WIDTH.getValue().doubleValue());
        colDisplayText.setPrefWidth(FOUND_TEXT_COLUMN_WIDTH.getValue().doubleValue());

        Platform.runLater(() -> {
            colVisualName.setPrefWidth(PATH_NAME_COLUMN_WIDTH.getValue().doubleValue());
            colIgnore.setPrefWidth(IGNORE_COLUMN_WIDTH.getValue().doubleValue());
            colAllowed.setPrefWidth(ALLOWED_COLUMN_WIDTH.getValue().doubleValue());
            colLine.setPrefWidth(LINE_COLUMN_WIDTH.getValue().doubleValue());
            colExactSignature.setPrefWidth(EXACT_SIGNATURE_COLUMN_WIDTH.getValue().doubleValue());
            colDisplayText.setPrefWidth(FOUND_TEXT_COLUMN_WIDTH.getValue().doubleValue());

            colVisualName.widthProperty().addListener(
                    (value, oldValue, newValue) -> PATH_NAME_COLUMN_WIDTH.parseValue(newValue));
            colIgnore.widthProperty().addListener(
                    (value, oldValue, newValue) -> IGNORE_COLUMN_WIDTH.parseValue(newValue));
            colAllowed.widthProperty().addListener(
                    (value, oldValue, newValue) -> ALLOWED_COLUMN_WIDTH.parseValue(newValue));
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
                        setStyle("-fx-background-color: #5cb574;");
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

    private HBox buildOnlineSignatureLoader(Stage primaryStage) {
        Label lbVersion = new Label("Online dictionary");
        Label lbPassw = new Label("Password");
        TextField tfPassword = new PasswordField();
        Button btnApply = new Button("Download latest dictionarry");

        HBox hBox = new HBox();
        hBox.setSpacing(8);

        hBox.getChildren().add(lbVersion);
        hBox.getChildren().add(lbPassw);
        hBox.getChildren().add(tfPassword);
        hBox.getChildren().add(new Separator(Orientation.VERTICAL));

        hBox.getChildren().add(btnApply);

        HBox.setHgrow(tfPassword, Priority.ALWAYS);
        lbVersion.setPrefWidth(DEFAULT_LABEL_WIDTH);
        btnApply.setPrefWidth(DEFAULT_BUTTON_WIDTH);

        tfPassword.setEditable(true);

        tfPassword.textProperty().addListener((bean, oldValue, newValue) -> PASSWORD.setValue(newValue));
        tfPassword.textProperty().setValue(PASSWORD.getValue());

        btnApply.setOnAction((event) -> {
            // check password and salt are set
            if (tfPassword.getText().isEmpty()) {
                AlertBuilder.showWarn("You need provide password for dictionary encryption");
            } else {
                String password = tfPassword.getText();
                ARunnable runnable = new RunnableCheckOnlineDictionary(false);
                runnable.setBeforeStart(() -> runOnFxThread(() -> btnApply.setDisable(true)));
                runnable.setAfterFinished(() -> {
                    try {
                        decryptOnlineDictionary(password);
                        loadDecryptedDictionary();
                    } finally {
                        runOnFxThread(() -> btnApply.setDisable(false));
                    }
                });
                runnable.startNowInNewThread();
            }
        });

        return hBox;
    }

    private void decryptOnlineDictionary(String password) {
        File fileDecrypted = new File(AppConstants.DICTIONARY_FILE_PATH_DECRYPTED);
        if (fileDecrypted.exists()) {
            boolean wasDeleted = fileDecrypted.delete();
            if (wasDeleted)
                log.info("Existed decrypted dictionary cache-file was deleted by {}", fileDecrypted);
        }

        try {
            String encryptedBody = FileUtils.readFile(AppConstants.DICTIONARY_FILE_PATH_ENCRYPTED);
            String decryptedBody = PasswordBasedEncryption.decrypt(encryptedBody, password);

            if (!decryptedBody.isEmpty()) {
                FileUtils.saveToFile(decryptedBody, AppConstants.DICTIONARY_FILE_PATH_DECRYPTED);
                log.info("New decrypted dictionary cache-file was successfully created at {}", fileDecrypted);
            }
        } catch (Exception ex) {
            log.error("Error while decrypting file {}. Check password and salt values!", fileDecrypted, ex);
        }
    }

    private void loadDecryptedDictionary() {
        Path sigsPath = Paths.get(AppConstants.DICTIONARY_FILE_PATH_DECRYPTED);
        File sigsFile = sigsPath.toFile();
        if (sigsFile.exists() && sigsFile.isFile()) {
            loadSignatures(sigsPath, null);
        }
    }

    private void loadSignatures(Path sigsPath, Button buttonToDisable) {
        if (!dictionaryLoading.compareAndSet(false, true)) {
            log.warn("Dictionary loading is already in progress");
            return;
        }

        RunnableSigsLoader loader = new RunnableSigsLoader(false);
        try {
            loader.setInputStream(FileUtils.toFileInputStream(sigsPath));
        } catch (RuntimeException ex) {
            dictionaryLoading.set(false);
            if (buttonToDisable != null) {
                runOnFxThread(() -> buttonToDisable.setDisable(false));
            }
            log.error("Failed to open dictionary file {}", sigsPath, ex);
            return;
        }
        loader.setBeforeStart(() -> {
            if (buttonToDisable != null) {
                runOnFxThread(() -> buttonToDisable.setDisable(true));
            }
        });
        loader.setAfterFinished(() -> {
            try {
                if (loader.isReady()) {
                    signaturesMap = loader.getSignaturesMap();
                    allowedSignaturesMap = loader.getAllowedSignaturesMap();
                    excludeExtMap = loader.getExcludeExtsMap();
                }
            } finally {
                dictionaryLoading.set(false);
                if (buttonToDisable != null) {
                    runOnFxThread(() -> buttonToDisable.setDisable(false));
                }
            }
        });
        loader.startNowInNewThread();
    }

    private void showScannerMessage(FxCallback.FxCallbackType type, String message) {
        switch (type) {
            case ERROR, WARNING -> AlertBuilder.showError(message);
            case INFO -> AlertBuilder.showInfo(message);
            default -> throw new IllegalStateException("Unsupported message type " + type);
        }
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
