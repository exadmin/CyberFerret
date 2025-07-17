package com.github.exadmin.cyberferret.fxui;

import com.github.exadmin.cyberferret.async.*;
import com.github.exadmin.cyberferret.exclude.Excluder;
import com.github.exadmin.cyberferret.fxui.helpers.AlertBuilder;
import com.github.exadmin.cyberferret.fxui.helpers.ChooserBuilder;
import com.github.exadmin.cyberferret.model.FoundFileItemListener;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import com.github.exadmin.cyberferret.utils.FileUtils;
import com.github.exadmin.cyberferret.utils.PasswordBasedEncryption;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeTableCell;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.github.exadmin.cyberferret.fxui.FxConstants.*;
import static com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager.*;

public class SceneBuilder {
    private static final Logger log = LoggerFactory.getLogger(SceneBuilder.class);

    private final Stage primaryStage;
    private final FoundItemsContainer foundItemsContainer;
    private final ObjectProperty<TreeItem<FoundPathItem>> selectedItemProperty = new SimpleObjectProperty<>();
    private final RunnableSigsLoader runnableSigsLoader;
    private final RunnableScanner runnableScanner;

    public SceneBuilder(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.foundItemsContainer = new FoundItemsContainer();
        this.runnableSigsLoader = new RunnableSigsLoader();
        this.runnableScanner = new RunnableScanner();
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

        TitledPane tpOnlineDictionary = createOnlineDictionaryPane();
        TitledPane tpOfflineDictionare = createOfflineDictionaryPane();
        TitledPane tpRepository = createRepositoryGroup();
        TitledPane tpExplorer = createExplorerGroup(tabPane);
        TitledPane tpLogs = createLogsPane();

        BorderPane bpRoot = new BorderPane();
        Pane wrapper = new StackPane(bpRoot);
        tab.setContent(wrapper);
        bpRoot.prefWidthProperty().bind(wrapper.widthProperty());
        bpRoot.prefHeightProperty().bind(wrapper.heightProperty());

        VBox vBox = new VBox();
        Accordion accordion = new Accordion();
        {
            accordion.getPanes().add(tpOnlineDictionary);
            accordion.getPanes().add(tpOfflineDictionare);
            accordion.setExpandedPane(tpOnlineDictionary);
        }
        vBox.getChildren().add(accordion);
        vBox.getChildren().add(tpRepository);

        bpRoot.setTop(vBox);
        bpRoot.setCenter(tpExplorer);
        bpRoot.setBottom(tpLogs);

        bpRoot.setPadding(new Insets(1));
        vBox.setSpacing(2);

        return tab;
    }

    protected TitledPane createOnlineDictionaryPane() {
        TitledPane tpOnlineDictionary = new TitledPane();
        tpOnlineDictionary.setCollapsible(true);
        tpOnlineDictionary.setText("Online Dictionary (recommended)");

        VBox vBoxRoot = new VBox();
        tpOnlineDictionary.setContent(vBoxRoot);
        vBoxRoot.setSpacing(8);

        // Online signature loader
        {
            HBox hBox = buildOnlineSignatureLoader(primaryStage);
            vBoxRoot.getChildren().add(hBox);
        }

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

            runnableSigsLoader.setBeforeStart(() -> btnLoadSigs.setDisable(true));
            runnableSigsLoader.setAfterFinished(() -> {
                runnableScanner.setSignaturesMap(runnableSigsLoader.getRegExpMap());
                runnableScanner.setAllowedSigMap(runnableSigsLoader.getAllowedSignaturesMap());
                runnableScanner.setExcludeExtMap(runnableSigsLoader.getExcludeExtsMap());
                btnLoadSigs.setDisable(false);
            });

            btnLoadSigs.setOnAction(event -> {
                if (DICTIONARY.getValue() != null && !DICTIONARY.getValue().isEmpty()) {
                    Path sigsPath = Paths.get(DICTIONARY.getValue());

                    runnableSigsLoader.setFileToLoad(sigsPath);
                    runnableSigsLoader.startNowInNewThread();
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

            runnableScanner.setBeforeStart(() -> btnRun.setDisable(true));
            runnableScanner.setAfterFinished(() -> btnRun.setDisable(false));

            btnRun.setOnAction(actionEvent -> {
                log.debug("Start button is pressed using dictionary {}, dir-to-scan = {}", DICTIONARY.getValue(), DIR_TO_SCAN.getValue());

                // drop previous scan result
                foundItemsContainer.clearAll();

                runnableScanner.setDirToScan(DIR_TO_SCAN.getValue());
                runnableScanner.setFoundItemsContainer(foundItemsContainer);
                runnableScanner.startNowInNewThread();
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

        colVisualName.setCellValueFactory(new TreeItemPropertyValueFactory<>("visualName"));
        colIgnore.setCellValueFactory(new TreeItemPropertyValueFactory<>("ignored"));
        colAllowed.setCellValueFactory(new TreeItemPropertyValueFactory<>("allowedValue"));
        colLine.setCellValueFactory(new TreeItemPropertyValueFactory<>("lineNumber"));
        colDisplayText.setCellValueFactory(new TreeItemPropertyValueFactory<>("displayText"));
        colExactSignature.setCellValueFactory(new TreeItemPropertyValueFactory<>("foundString"));

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
        colExactSignature.setSortable(false);
        colExactSignature.setEditable(false);
        colDisplayText.setEditable(false);
        colDisplayText.setSortable(false);
        colAllowed.setEditable(false);
        colAllowed.setSortable(false);

        colVisualName.prefWidthProperty().setValue(200d);

        TreeTableView<FoundPathItem> ttView = new TreeTableView<>();
        ttView.getColumns().add(colVisualName);
        ttView.getColumns().add(colIgnore);
        ttView.getColumns().add(colAllowed);
        ttView.getColumns().add(colLine);
        ttView.getColumns().add(colDisplayText);
        ttView.getColumns().add(colExactSignature);

        ttView.setEditable(false);
        ttView.setShowRoot(false);
        ttView.setMinHeight(320);

        ttView.setRowFactory(tv -> new TreeTableRow<FoundPathItem>() {
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
                    } else if (!isSelected && !foundPathItem.foundStringProperty().isEmpty().get()) {
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
            }

            @Override
            public void onClearAll() {
                rootTreeItem.getChildren().clear();
            }
        });

        ttView.getSelectionModel().selectedItemProperty().addListener((bean, oldItem, newItem) -> {
            // cache selected item
            selectedItemProperty.setValue(newItem);
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

    private HBox buildOnlineSignatureLoader(Stage primaryStage) {
        Label lbVersion = new Label("Online dictionary");
        Label lbPassw = new Label("Password");
        TextField tfPassword = new PasswordField();
        Button btnCheckUpdates = new Button("Download");
        Button btnDecrypt = new Button("Decrypt");
        Button btnApply = new Button("Load decrypted");
        Label lbSalt = new Label("Salt");
        TextField tfSalt = new PasswordField();

        HBox hBox = new HBox();
        hBox.setSpacing(8);

        hBox.getChildren().add(lbVersion);
        hBox.getChildren().add(btnCheckUpdates);
        hBox.getChildren().add(new Separator(Orientation.VERTICAL));

        hBox.getChildren().add(lbPassw);
        hBox.getChildren().add(tfPassword);
        hBox.getChildren().add(lbSalt);
        hBox.getChildren().add(tfSalt);
        hBox.getChildren().add(new Separator(Orientation.VERTICAL));

        hBox.getChildren().add(btnDecrypt);
        hBox.getChildren().add(btnApply);

        HBox.setHgrow(tfPassword, Priority.ALWAYS);
        lbVersion.setPrefWidth(DEFAULT_LABEL_WIDTH);
        btnCheckUpdates.setPrefWidth(DEFAULT_BUTTON_WIDTH);
        btnApply.setPrefWidth(DEFAULT_BUTTON_WIDTH);
        btnDecrypt.setPrefWidth(DEFAULT_BUTTON_WIDTH);

        tfPassword.setEditable(true);

        tfPassword.textProperty().addListener((bean, oldValue, newValue) -> PASSWORD.setValue(newValue));
        tfPassword.textProperty().setValue(PASSWORD.getValue());

        tfSalt.textProperty().addListener((bean, oldValue, newValue) -> SALT.setValue(newValue));
        tfSalt.textProperty().setValue(SALT.getValue());

        btnCheckUpdates.setOnAction((event) -> {
            ARunnable runnable = new RunnableCheckOnlineDictionary();
            runnable.setBeforeStart(() -> btnCheckUpdates.setDisable(true));
            runnable.setAfterFinished(() -> btnCheckUpdates.setDisable(false));
            runnable.startNowInNewThread();
        });

        btnDecrypt.setOnAction((event) -> {
            // check password and salt are set
            if (tfPassword.getText().isEmpty() || tfSalt.getText().isEmpty()) {
                AlertBuilder.showWarn("You need provide password and salt for dictionary encryption");
            } else {
                File fileDecrypted = new File(FxConstants.DICTIONARY_FILE_PATH_DECRYPTED);
                if (fileDecrypted.exists()) {
                    boolean wasDeleted = fileDecrypted.delete();
                    if (wasDeleted)
                        log.info("Existed decrypted dictionary cache-file was deleted by {}", fileDecrypted);
                }

                try {
                    String encryptedBody = FileUtils.readFile(DICTIONARY_FILE_PATH_ENCRYPTED);
                    String decryptedBody = PasswordBasedEncryption.decrypt(encryptedBody, tfPassword.getText(), tfSalt.getText());

                    if (decryptedBody != null && !decryptedBody.isEmpty()) {
                        FileUtils.saveToFile(decryptedBody, FxConstants.DICTIONARY_FILE_PATH_DECRYPTED);
                        log.info("New decrypted dictionary cache-file was successfully created at {}", fileDecrypted);
                    }
                } catch (Exception ex) {
                    log.error("Error while decrypting file {}. Check password and salt values!", fileDecrypted, ex);
                }
            }
        });

        btnApply.setOnAction((event) -> {
            Path sigsPath = Paths.get(DICTIONARY_FILE_PATH_DECRYPTED);
            File sigsFile = sigsPath.toFile();
            if (sigsFile.exists() && sigsFile.isFile()) {
                runnableSigsLoader.setFileToLoad(sigsPath);
                runnableSigsLoader.startNowInNewThread();
            }
        });

        return hBox;
    }

    private class MarkAsIgnoredEventHandler implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent event) {
            if (selectedItemProperty.getValue() == null) {
                AlertBuilder.showInfo("No items are selected to be marked as ignored!");
            } else {
                FoundPathItem foundPathItem = selectedItemProperty.getValue().getValue();
                Path resultYaml = Excluder.markToExclude(foundPathItem, Paths.get(DIR_TO_SCAN.toString()));
                log.info("Item {} was successfully {} as ignored, the result is stored into {}", foundPathItem, foundPathItem.isIgnored() ? "(+)marked" : "(-)unmarked", resultYaml);
            }
        }
    }
}
