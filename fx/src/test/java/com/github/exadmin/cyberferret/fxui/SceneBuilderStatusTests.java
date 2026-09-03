package com.github.exadmin.cyberferret.fxui;

import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneBuilderStatusTests {
    @Test
    void usesRequestedExcludedRowColor() {
        assertEquals("-fx-background-color: #f0e73a;", SceneBuilder.EXCLUDED_ROW_STYLE);
    }

    @Test
    void mapsTreeItemsToDisplayedStatuses() {
        FoundPathItem folder = item("folder", ItemType.DIRECTORY);
        FoundPathItem file = item("file.txt", ItemType.FILE);
        FoundPathItem excludedFolder = item("excluded-folder", ItemType.DIRECTORY);
        excludedFolder.setIgnored(true);
        FoundPathItem excludedFile = item("excluded-file.txt", ItemType.FILE);
        excludedFile.setIgnored(true);
        FoundPathItem allowed = item("allowed", ItemType.SIGNATURE);
        allowed.setAllowedValue(true);
        FoundPathItem excluded = item("excluded", ItemType.SIGNATURE);
        excluded.setIgnored(true);
        excluded.setAllowedValue(true);
        FoundPathItem warning = item("warning", ItemType.SIGNATURE);

        assertEquals("Folder", SceneBuilder.statusFor(folder));
        assertEquals("File", SceneBuilder.statusFor(file));
        assertEquals("Excluded", SceneBuilder.statusFor(excludedFolder));
        assertEquals("Excluded", SceneBuilder.statusFor(excludedFile));
        assertEquals("Allowed", SceneBuilder.statusFor(allowed));
        assertEquals("Excluded", SceneBuilder.statusFor(excluded));
        assertEquals("Warning", SceneBuilder.statusFor(warning));
    }

    @Test
    void mapsItemStateAndSelectionToRowStyle() {
        FoundPathItem ignored = item("ignored", ItemType.SIGNATURE);
        ignored.setIgnored(true);
        FoundPathItem allowed = item("allowed", ItemType.SIGNATURE);
        allowed.setAllowedValue(true);
        FoundPathItem found = item("found", ItemType.SIGNATURE);
        found.setFoundString("secret");

        assertEquals(SceneBuilder.EXCLUDED_ROW_STYLE, SceneBuilder.rowStyleFor(ignored, false));
        assertEquals("-fx-background-color: #c1f7cf;", SceneBuilder.rowStyleFor(allowed, false));
        assertEquals("-fx-background-color: #f2d0d0;", SceneBuilder.rowStyleFor(found, false));
        assertEquals("", SceneBuilder.rowStyleFor(ignored, true));
    }

    @Test
    void prefersContextMenuItemAsExclusionTarget() {
        FoundPathItem contextMenuItem = item("context-menu-item", ItemType.FILE);
        FoundPathItem selectedItem = item("selected-item", ItemType.FILE);

        assertSame(contextMenuItem, SceneBuilder.exclusionTarget(contextMenuItem, selectedItem));
        assertSame(selectedItem, SceneBuilder.exclusionTarget(null, selectedItem));
    }

    @Test
    void matchesConfiguredDirectoryToScanResultsRoot() {
        Path scanResultsRoot = Path.of("repository").toAbsolutePath().normalize();

        assertTrue(SceneBuilder.matchesScanResultsRoot(scanResultsRoot, scanResultsRoot.toString()));
        assertFalse(SceneBuilder.matchesScanResultsRoot(scanResultsRoot, Path.of("other").toString()));
        assertFalse(SceneBuilder.matchesScanResultsRoot(scanResultsRoot, "\0"));
    }

    @Test
    void restoresWorkerStateWhenStartupFails() {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean rolledBack = new AtomicBoolean();
        AtomicReference<RuntimeException> reportedFailure = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("startup failed");

        SceneBuilder.startWorker(
                running,
                () -> {
                    throw failure;
                },
                () -> rolledBack.set(true),
                reportedFailure::set);

        assertFalse(running.get());
        assertTrue(rolledBack.get());
        assertSame(failure, reportedFailure.get());
    }

    private static FoundPathItem item(String path, ItemType type) {
        return new FoundPathItem(Path.of(path), type, null);
    }
}
