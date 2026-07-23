package com.github.exadmin.cyberferret.cfcli;

import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CfCliTreeAssemblerTests {
    @TempDir
    Path root;

    @Test
    public void buildsNestedTreeAndMapsEverySignatureStatus() throws Exception {
        Path file = root.resolve("src/nested/file.txt");
        Files.createDirectories(file.getParent());
        String body = "FOUND ALLOWED EXCLUDED";
        Files.writeString(file, body, StandardCharsets.UTF_8);
        FoundItemsContainer container = new FoundItemsContainer();
        CfCliTreeAssembler assembler = new CfCliTreeAssembler(root, container, ignored -> {
        });

        assembler.accept(message("list", null, "src", null, null, null));
        assembler.accept(message("list", null, "src/nested", null, null, null));
        assembler.accept(message("list", "src/nested/file.txt", null, null, null, null));
        assembler.accept(message("found", "src/nested/file.txt", null, "F", "FOUND", 1L));
        assembler.accept(message("allowed", "src/nested/file.txt", null, "A", "ALLOWED", 1L));
        assembler.accept(message("excluded", "src/nested/file.txt", null, "E", "EXCLUDED", 1L));

        List<FoundPathItem> items = container.getFoundItemsCopy();
        assertEquals(6, items.size());
        assertEquals(List.of("src", "nested", "file.txt", "F", "A", "E"),
                items.stream().map(FoundPathItem::getVisualName).toList());
        assertEquals(ItemType.DIRECTORY, items.get(0).getType());
        assertEquals(items.get(0), items.get(1).getParent());
        assertEquals(items.get(1), items.get(2).getParent());
        assertEquals(items.get(2), items.get(3).getParent());
        assertFalse(items.get(3).isAllowedValue());
        assertTrue(items.get(4).isAllowedValue());
        assertTrue(items.get(5).isIgnored());
        assertEquals(1, items.get(3).getLineNumber());
        assertEquals(body, items.get(3).getDisplayText());
        assertEquals("FOUND", items.get(3).getFoundString());
    }

    @Test
    public void deduplicatesPathsAndAppliesPathExclusion() throws Exception {
        FoundItemsContainer container = new FoundItemsContainer();
        CfCliTreeAssembler assembler = new CfCliTreeAssembler(root, container, ignored -> {
        });

        assembler.accept(message("list", null, "generated", null, null, null));
        assembler.accept(message("list", null, "generated", null, null, null));
        assembler.accept(message("excluded", "generated", null, null, null, null));

        assertEquals(1, container.getFoundItemsSize());
        assertTrue(container.getFoundItemsCopy().getFirst().isIgnored());
    }

    @Test
    public void rejectsPathsOutsideTheScanRoot() {
        CfCliTreeAssembler assembler = new CfCliTreeAssembler(root, new FoundItemsContainer(), ignored -> {
        });

        assertThrows(IOException.class, () -> assembler.accept(
                message("list", "../outside.txt", null, null, null, null)));
    }

    @Test
    public void keepsSignatureWhenFileCannotBeRead() throws Exception {
        FoundItemsContainer container = new FoundItemsContainer();
        List<String> warnings = new ArrayList<>();
        CfCliTreeAssembler assembler = new CfCliTreeAssembler(root, container, warnings::add);

        assembler.accept(message("list", "missing.txt", null, null, null, null));
        assembler.accept(message("found", "missing.txt", null, "KEY", "VALUE", 1L));

        FoundPathItem signature = container.getFoundItemsCopy().getLast();
        assertEquals(ItemType.SIGNATURE, signature.getType());
        assertEquals(0, signature.getLineNumber());
        assertEquals("", signature.getDisplayText());
        assertEquals(1, warnings.size());
    }

    private static CfCliMessage message(
            String type,
            String file,
            String folder,
            String key,
            String found,
            Long line) {
        return new CfCliMessage(type, file, folder, key, found, line);
    }
}
