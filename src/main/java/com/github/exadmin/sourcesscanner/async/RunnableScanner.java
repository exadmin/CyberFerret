package com.github.exadmin.sourcesscanner.async;

import com.github.exadmin.sourcesscanner.model.FoundItemsContainer;
import com.github.exadmin.sourcesscanner.model.FoundPathItem;
import com.github.exadmin.sourcesscanner.model.ItemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunnableScanner extends ARunnable {
    private static final Logger log = LoggerFactory.getLogger(RunnableScanner.class);

    private String signaturesFile;
    private String dirToScan;
    private FoundItemsContainer foundItemsContainer;
    private Map<String, Pattern> sigMap = null;

    public RunnableScanner() {
    }

    public void setSignaturesMap(Map<String, Pattern> sigMap) {
        this.sigMap = sigMap;
    }

    public void setSignaturesFile(String signaturesFile) {
        this.signaturesFile = signaturesFile;
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
        if (sigMap == null || sigMap.isEmpty()) {
            log.error("No signatures are loaded. Stop scanning");
            return;
        }

        // load files first
        Deque<FoundPathItem> parentsDeque = new ArrayDeque<>();

        Path startDir = Paths.get(dirToScan);
        Files.walkFileTree(startDir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                log.debug("Visiting directory {}", dir);

                FoundPathItem parent = parentsDeque.peekLast();
                FoundPathItem foundPathItem = new FoundPathItem(dir, ItemType.DIRECTORY, parent);
                foundItemsContainer.addItem(foundPathItem);
                parentsDeque.add(foundPathItem);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.debug("Visiting file {}", file);

                FoundPathItem parent = parentsDeque.peekLast();
                FoundPathItem foundPathItem = new FoundPathItem(file, ItemType.FILE, parent);
                foundItemsContainer.addItem(foundPathItem);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                log.error("Error while visiting {}", file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                parentsDeque.removeLast();
                return FileVisitResult.CONTINUE;
            }
        });

        // start scanning for signatures
        int totalItemsCount = foundItemsContainer.getFoundItems().size();
        int processedItemsCount = 0;
        int nextRate = 0;

        List<FoundPathItem> list = new ArrayList<>(foundItemsContainer.getFoundItems());
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
                    newItem.setStartPlace(matcher.start());
                    newItem.setEndPlace(matcher.end());

                    foundItemsContainer.addItem(newItem);
                    log.info("Signature {} is detected in {}", sigId, filePath);
                }
            }

            pathItem.setStatus("Scanned");
        }
    }

    public static String readFile(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        return new String(bytes);
    }
}
