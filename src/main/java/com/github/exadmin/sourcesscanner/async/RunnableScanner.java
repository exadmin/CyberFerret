package com.github.exadmin.sourcesscanner.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Callable;

public class RunnableScanner implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RunnableScanner.class);

    private String signaturesFile;
    private String dirToScan;
    private Callable<?> beforeStart;
    private Callable<?> afterFinished;

    public RunnableScanner(String signaturesFile, String dirToScan) {
        this.signaturesFile = signaturesFile;
        this.dirToScan = dirToScan;
    }

    public void setBeforeStart(Callable beforeStart) {
        this.beforeStart = beforeStart;
    }

    public void setAfterFinished(Callable afterFinished) {
        this.afterFinished = afterFinished;
    }

    @Override
    public void run() {
        try {
            if (beforeStart != null) beforeStart.call();
            _run();
            if (afterFinished != null) afterFinished.call();
        } catch (Exception ex) {
            log.error("Error during scan running", ex);
        }
    }

    private void _run() throws IOException {
        Path startDir = Paths.get(dirToScan);
        Files.walkFileTree(startDir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.debug("Visiting file {}", file);

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
