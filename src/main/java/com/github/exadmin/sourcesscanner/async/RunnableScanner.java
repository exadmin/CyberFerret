package com.github.exadmin.sourcesscanner.async;

import com.github.exadmin.sourcesscanner.model.FoundPathItem;
import com.github.exadmin.sourcesscanner.model.FoundItemsContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class RunnableScanner implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RunnableScanner.class);

    private String signaturesFile;
    private String dirToScan;
    private Runnable beforeStart;
    private Runnable afterFinished;
    private FoundItemsContainer foundItemsContainer;

    public RunnableScanner(String signaturesFile, String dirToScan, FoundItemsContainer container) {
        this.signaturesFile = signaturesFile;
        this.dirToScan = dirToScan;
        this.foundItemsContainer = container;
    }

    public void setBeforeStart(Runnable beforeStart) {
        this.beforeStart = beforeStart;
    }

    public void setAfterFinished(Runnable afterFinished) {
        this.afterFinished = afterFinished;
    }

    @Override
    public void run() {
        try {
            if (beforeStart != null) beforeStart.run();
            _run();
            if (afterFinished != null) afterFinished.run();
        } catch (Exception ex) {
            log.error("Error during scan running", ex);
        }
    }

    private void _run() throws IOException {
        Path startDir = Paths.get(dirToScan);
        Files.walkFileTree(startDir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                log.debug("Visiting directory {}", dir);

                FoundPathItem foundPathItem = new FoundPathItem(dir);
                foundItemsContainer.addItem(foundPathItem);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.debug("Visiting file {}", file);

                FoundPathItem foundPathItem = new FoundPathItem(file);
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
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
