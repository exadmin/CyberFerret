package com.github.exadmin.cyberferret.async;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.exadmin.cyberferret.exclude.ExcludeFileModel;
import com.github.exadmin.cyberferret.exclude.Excluder;
import com.github.exadmin.cyberferret.fxui.helpers.AlertBuilder;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import com.github.exadmin.cyberferret.utils.MiscUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunnableScanner extends ARunnable {
    private static final Logger log = LoggerFactory.getLogger(RunnableScanner.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String dirToScan;
    private FoundItemsContainer foundItemsContainer;
    private Map<String, Pattern> sigMap = null;
    private Map<String, String> allowedSigMap = null;

    public RunnableScanner() {
    }

    public void setSignaturesMap(Map<String, Pattern> sigMap) {
        this.sigMap = sigMap;
    }

    public void setAllowedSigMap(Map<String, String> allowedSigMap) {
        this.allowedSigMap = allowedSigMap;
    }

    public void setDirToScan(String dirToScan) {
        this.dirToScan = dirToScan;
    }

    public void setFoundItemsContainer(FoundItemsContainer foundItemsContainer) {
        this.foundItemsContainer = foundItemsContainer;
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
            AlertBuilder.showWarn("You've selected not a root of a git-repository.\n" +
                    "You can continue using scanner but exclusion file may be created/written not in the canonical place.\n" +
                    "Existed exclusion configurations will not be shown");
        }

        if (sigMap == null || sigMap.isEmpty()) {
            AlertBuilder.showError("Load signatures first. Nothing to scan by.");
            return;
        }

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

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)  {
                log.debug("Visiting file {}", file);

                FoundPathItem parent = parentsDeque.peekLast();
                FoundPathItem foundPathItem = new FoundPathItem(file, ItemType.FILE, parent);
                foundItemsContainer.addItem(foundPathItem);

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

        // try loading exclusions-model from the file in the root of the repository
        ExcludeFileModel excludeFileModel = new ExcludeFileModel(); // create empty container
        Path exFile = Paths.get(dirToScan, Excluder.PERSISTENCE_FOLDER, Excluder.EXCLUDES_SHORT_FILE_NAME);
        try {
            OBJECT_MAPPER.enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);
            excludeFileModel = OBJECT_MAPPER.readValue(exFile.toFile(), ExcludeFileModel.class); // load new exclusion context
        } catch (Exception ex) {
            log.error("Error while loading exclusions configuration from '{}' file", exFile, ex);
        }

        // start scanning for signatures
        int totalItemsCount = foundItemsContainer.getFoundItemsSize();
        int processedItemsCount = 0;
        int nextRate = 0;

        List<FoundPathItem> list = foundItemsContainer.getFoundItemsCopy();
        for (FoundPathItem pathItem : list) {
            // update progress
            processedItemsCount++;
            int progressRate =  processedItemsCount * 100 / totalItemsCount;
            if (progressRate > nextRate) {
                log.info("Scanned rate = {}%", progressRate);
                nextRate = nextRate + 10;
            }

            // we do scan only file-items
            if (pathItem.getType() == ItemType.DIRECTORY || pathItem.getType() == ItemType.SIGNATURE) continue;

            Path filePath = pathItem.getFilePath();
            String fileBody = readFile(filePath);
            for (Map.Entry<String, Pattern> me : sigMap.entrySet()) {
                String sigId = me.getKey();
                Pattern regExp = me.getValue();

                Matcher matcher = regExp.matcher(fileBody);
                if (matcher.find()) {
                    FoundPathItem newItem = new FoundPathItem(filePath, ItemType.SIGNATURE, pathItem);
                    newItem.setVisualName(sigId);
                    newItem.setLineNumber(getLineNumber(fileBody, matcher.start()));
                    newItem.setDisplayText(getText(fileBody, matcher.start(), matcher.end()));
                    newItem.setFoundString(matcher.group());

                    // check if we have already marked found signature as ignored
                    String relFileName = MiscUtils.getRelativeFileName(rootDir, newItem.getFilePath());
                    String textHash = MiscUtils.getSHA256AsHex(newItem.getFoundString());
                    String fileHash = MiscUtils.getSHA256AsHex(relFileName);

                    newItem.setIgnored(excludeFileModel.contains(textHash, fileHash));

                    // check if item is in the allowed list
                    if (allowedSigMap.containsValue(newItem.getFoundString())) {
                        newItem.setAllowedValue(true);
                    }

                    foundItemsContainer.addItem(newItem);
                    log.info("Signature {} is detected in {}", sigId, filePath);
                }
            }
        }

        log.info("Scanning completed for 100%");
    }

    public static String readFile(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        return new String(bytes);
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
}
