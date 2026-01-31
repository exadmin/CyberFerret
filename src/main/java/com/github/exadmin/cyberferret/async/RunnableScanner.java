package com.github.exadmin.cyberferret.async;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.exadmin.cyberferret.exclude.ExcludeFileModel;
import com.github.exadmin.cyberferret.exclude.Excluder;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import com.github.exadmin.cyberferret.utils.FileUtils;
import com.github.exadmin.cyberferret.utils.MiscUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunnableScanner extends ARunnable {
    private static final Logger log = LoggerFactory.getLogger(RunnableScanner.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String dirToScan;
    private FoundItemsContainer foundItemsContainer;
    private Map<String, Pattern> sigMap = null;
    private Map<String, String> allowedSigMap = null;
    private Map<String, List<String>> excludeExtMap = null;
    private FxCallback fxCallback = (type, message) -> {
        log.info(message);
    };

    public RunnableScanner() {
    }

    public void setSignaturesMap(Map<String, Pattern> sigMap) {
        this.sigMap = sigMap;
    }

    public void setAllowedSigMap(Map<String, String> allowedSigMap) {
        this.allowedSigMap = allowedSigMap;
    }

    public void setExcludeExtMap(Map<String, List<String>> excludeExtMap) {
        this.excludeExtMap = excludeExtMap;
    }

    public void setDirToScan(String dirToScan) {
        this.dirToScan = dirToScan;
    }

    public void setFoundItemsContainer(FoundItemsContainer foundItemsContainer) {
        this.foundItemsContainer = foundItemsContainer;
    }

    public void setFxCallback(FxCallback fxCallback) {
        this.fxCallback = fxCallback;
    }

    @Override
    protected Logger getLog() {
        return log;
    }

    @Override
    protected void _run() throws IOException {
        final Path rootDir = Paths.get(dirToScan);
        // check that root of the git-repository is selected - otherwise show warning
        Path gitConfigPath = Paths.get(dirToScan, ".git", "config");
        File gitConfigFile = gitConfigPath.toFile();
        if (!gitConfigFile.exists() || !gitConfigFile.isFile()) {
            fxCallback.showMessage(FxCallback.FxCallbackType.WARNING, """
                    You've selected not a root of a git-repository.
                    You can continue using scanner but exclusion file may be created/written not in the canonical place.
                    Existed exclusion configurations will not be shown""");
        }

        if (sigMap == null || sigMap.isEmpty()) {
            fxCallback.showMessage(FxCallback.FxCallbackType.ERROR, "Load signatures first. Nothing to scan by.");
            return;
        }

        // try loading exclusions-model from the file in the root of the repository
        ExcludeFileModel tmpExcludeFileModel = new ExcludeFileModel(); // create empty container
        Path exFilePath = Paths.get(dirToScan, Excluder.PERSISTENCE_FOLDER, Excluder.EXCLUDES_SHORT_FILE_NAME);
        try {
            File exFile = exFilePath.toFile();
            if (exFile.exists() && exFile.isFile()) {
                OBJECT_MAPPER.enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);
                tmpExcludeFileModel = OBJECT_MAPPER.readValue(exFile, ExcludeFileModel.class); // load new exclusion context
            }
        } catch (Exception ex) {
            log.error("Error while loading exclusions configuration from '{}' file", exFilePath, ex);
        }

        final ExcludeFileModel excludeFileModel = tmpExcludeFileModel;

        // load files first
        Deque<FoundPathItem> parentsDeque = new ArrayDeque<>();
        Files.walkFileTree(rootDir, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                log.debug("Visiting directory {}", dir);

                // todo: move this hard code to some configurable place, priority = normal
                if (dir.getFileName().toString().equals(".git")) return FileVisitResult.SKIP_SUBTREE;

                FoundPathItem parent = parentsDeque.peekLast();
                FoundPathItem foundPathItem = new FoundPathItem(dir, ItemType.DIRECTORY, parent);
                foundItemsContainer.addItem(foundPathItem);
                parentsDeque.add(foundPathItem);

                calculateIgnoreFlagState(foundPathItem, parent, rootDir, excludeFileModel);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)  {
                log.debug("Visiting file {}", file);

                FoundPathItem parent = parentsDeque.peekLast();
                FoundPathItem foundPathItem = new FoundPathItem(file, ItemType.FILE, parent);
                foundItemsContainer.addItem(foundPathItem);

                calculateIgnoreFlagState(foundPathItem, parent, rootDir, excludeFileModel);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.error("Error while visiting {}", file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                parentsDeque.removeLast();
                return FileVisitResult.CONTINUE;
            }
        });



        // start scanning for signatures
        int totalItemsCount = foundItemsContainer.getFoundItemsSize();
        final AtomicInteger processedItemsCount = new AtomicInteger(0);
        final AtomicInteger nextRate = new AtomicInteger(0);

        List<FoundPathItem> list = foundItemsContainer.getFoundItemsCopy();
        final AtomicInteger numberOfThreadsInProgress = new AtomicInteger(0);

        // try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            list.forEach(pathItem -> {
                executor.submit(() -> {
                    numberOfThreadsInProgress.incrementAndGet();

                    // update progress rate
                    int currentCount = processedItemsCount.incrementAndGet();
                    int progressRate =  currentCount * 100 / totalItemsCount;
                    if (progressRate > nextRate.get()) {
                        // Platform.runLater(() -> {log.info("Scanned rate = {}%", progressRate);});

                        nextRate.addAndGet(10); // todo: non thread safe - refactor later
                    }


                    log.info("Threads in progress = {}, Scanning for {}", numberOfThreadsInProgress.get(), pathItem);

                    // do scan
                    scan(pathItem, rootDir, excludeFileModel, foundItemsContainer);

                    numberOfThreadsInProgress.decrementAndGet();
                });
            });
        }

        log.info("Scanning completed for 100%");
    }

    public static int getLineNumber(String fileBody, int index) {
        int lineNumber = 0;
        int charsCount = 0;
        List<String> lines = fileBody.lines().toList();
        for (String line : lines) {
            charsCount = charsCount + line.length();
            if (index < charsCount) return lineNumber;

            lineNumber++;
        }

        return lineNumber;
    }

    // number of chars to be shown additionally on the left and right sides of the found piece of text
    private static final int EXPAND_AREA_TO_BE_SHOWN_CHARS = 50;
    private static final int MAX_LENGTH_OF_SHOWN_TEXT = 200;

    public static String getText(String fileBody, int fromIndex, int toIndex) {
        fromIndex = fromIndex - EXPAND_AREA_TO_BE_SHOWN_CHARS;
        if (fromIndex < 0) fromIndex = 0;
        toIndex = toIndex + EXPAND_AREA_TO_BE_SHOWN_CHARS;
        if (toIndex > fromIndex + MAX_LENGTH_OF_SHOWN_TEXT) toIndex = fromIndex + MAX_LENGTH_OF_SHOWN_TEXT;
        if (toIndex > fileBody.length()) toIndex = fileBody.length();
        String text = fileBody.substring(fromIndex, toIndex);

        text = text.replaceAll("\\s", " ");
        return text;
    }

    private void scan(FoundPathItem pathItem, Path rootDir, ExcludeFileModel excludeFileModel, FoundItemsContainer foundItemsContainer) {
        // we do scan only file-items
        if (pathItem.getType() == ItemType.DIRECTORY || pathItem.getType() == ItemType.SIGNATURE) return;

        Path filePath = pathItem.getFilePath();
        String fileBody;
        try {
            log.info("Reading file {}", filePath);
            fileBody = FileUtils.readFile(filePath);
        } catch (IOException ex) {
            log.error("Error while reading file '{}'. Skipping it.", filePath, ex);
            return;
        }

        for (Map.Entry<String, Pattern> me : sigMap.entrySet()) {
            String sigId = me.getKey();
            Pattern regExp = me.getValue();

            if (isToIgnoreFile(sigId, filePath)) continue;

            Matcher matcher = regExp.matcher(fileBody);
            if (matcher.find()) {
                FoundPathItem newItem = new FoundPathItem(filePath, ItemType.SIGNATURE, pathItem);
                newItem.setVisualName(sigId);
                newItem.setLineNumber(getLineNumber(fileBody, matcher.start()));
                newItem.setDisplayText(getText(fileBody, matcher.start(), matcher.end()));
                newItem.setFoundString(matcher.group());

                calculateIgnoreFlagState(newItem, pathItem, rootDir, excludeFileModel);

                // check if item is in the allowed list
                if (allowedSigMap.containsValue(newItem.getFoundString())) {
                    newItem.setAllowedValue(true);
                }

                foundItemsContainer.addItem(newItem);
                log.info("Signature {} is detected in {}", sigId, filePath);
            }
        }
    }

    private boolean isToIgnoreFile(String signatureId, Path currentFile) {
        if (excludeExtMap == null) return false;
        List<String> list = excludeExtMap.get(signatureId);
        if (list == null) return false;

        String fileExt = FileUtils.getFileExtensionAsString(currentFile);
        if (fileExt == null) return false;

        return list.contains(fileExt);
    }

    private static void calculateIgnoreFlagState(FoundPathItem foundPathItem, FoundPathItem parent, Path rootDir, ExcludeFileModel excludeFileModel) {
        // check if Ignore-flag is specified directly for current folder
        String relFileName = MiscUtils.getRelativeFileName(rootDir, foundPathItem.getFilePath());
        String hash = MiscUtils.getSHA256AsHex(relFileName);
        String textHash = Excluder.HASH_IGNORE_CONTENT;
        if (ItemType.SIGNATURE.equals(foundPathItem.getType())) {
            textHash = MiscUtils.getSHA256AsHex(foundPathItem.getFoundString());
        }

        boolean isMarkedAsIgnored = excludeFileModel.contains(textHash, hash);
        // if no - then use flag state from parent item
        if (!isMarkedAsIgnored && parent != null) isMarkedAsIgnored = parent.isIgnored();

        foundPathItem.setIgnored(isMarkedAsIgnored);
    }
}
