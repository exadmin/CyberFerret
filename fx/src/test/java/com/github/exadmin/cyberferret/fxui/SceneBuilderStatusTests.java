package com.github.exadmin.cyberferret.fxui;

import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneBuilderStatusTests {
    @Test
    void usesRequestedExcludedRowColor() {
        assertEquals("-fx-background-color: #f0e73a;", SceneBuilder.EXCLUDED_ROW_STYLE);
    }

    @Test
    void mapsTreeItemsToDisplayedStatuses() {
        FoundPathItem folder = item("folder", ItemType.DIRECTORY);
        FoundPathItem file = item("file.txt", ItemType.FILE);
        FoundPathItem allowed = item("allowed", ItemType.SIGNATURE);
        allowed.setAllowedValue(true);
        FoundPathItem excluded = item("excluded", ItemType.SIGNATURE);
        excluded.setIgnored(true);
        FoundPathItem warning = item("warning", ItemType.SIGNATURE);

        assertEquals("Folder", SceneBuilder.statusFor(folder));
        assertEquals("File", SceneBuilder.statusFor(file));
        assertEquals("Allowed", SceneBuilder.statusFor(allowed));
        assertEquals("Excluded", SceneBuilder.statusFor(excluded));
        assertEquals("Warning", SceneBuilder.statusFor(warning));
    }

    private static FoundPathItem item(String path, ItemType type) {
        return new FoundPathItem(Path.of(path), type, null);
    }
}
