package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunnableScannerTests {
    @TempDir
    Path tempDir;

    @Test
    public void cliMode_scansOnlyStagedFilesWithoutWalkingWholeRepository() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);
        Files.createDirectories(repoRoot.resolve("nested/deeper"));

        Path stagedFile = repoRoot.resolve("nested/deeper/staged.txt");
        Path notStagedFile = repoRoot.resolve("not-staged.txt");
        Files.writeString(stagedFile, "clean", StandardCharsets.UTF_8);
        Files.writeString(notStagedFile, "clean", StandardCharsets.UTF_8);

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setPrintToConsole(true);
        runnableScanner.setDirToScan(repoRoot.toString());
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        runnableScanner.setAllowedSignaturesMap(Map.of());
        runnableScanner.setExcludeExtMap(Map.of());
        runnableScanner.setStagedFiles(List.of(stagedFile));

        runnableScanner.run();

        List<FoundPathItem> foundItems = foundItemsContainer.getFoundItemsCopy();
        assertEquals(1, foundItems.size());
        assertEquals(ItemType.FILE, foundItems.getFirst().getType());
        assertEquals(stagedFile.toAbsolutePath().normalize(), foundItems.getFirst().getFilePath());
    }

    @Test
    public void cliMode_withoutStagedFilesDoesNotScanWholeRepository() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);

        Path repoFile = repoRoot.resolve("not-staged.txt");
        Files.writeString(repoFile, "secret", StandardCharsets.UTF_8);

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setDirToScan(repoRoot.toString());
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        runnableScanner.setAllowedSignaturesMap(Map.of());
        runnableScanner.setExcludeExtMap(Map.of());

        runnableScanner.run();

        assertTrue(foundItemsContainer.getFoundItemsCopy().isEmpty());
        assertFalse(runnableScanner.isAnySignatureFound());
    }

    @Test
    public void cliMode_findsSignatureInUtf16LeFileCreatedByWindowsPowershell() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);

        Path stagedFile = repoRoot.resolve("2.txt");
        Files.write(stagedFile, utf16LeWithBom("hack hack\r\n"));

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setDirToScan(repoRoot.toString());
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(Map.of("test", Pattern.compile("hack")));
        runnableScanner.setAllowedSignaturesMap(Map.of());
        runnableScanner.setExcludeExtMap(Map.of());
        runnableScanner.setStagedFiles(List.of(stagedFile));

        runnableScanner.run();

        assertTrue(runnableScanner.isAnySignatureFound());
    }

    private static byte[] utf16LeWithBom(String value) {
        byte[] content = value.getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer buffer = ByteBuffer.allocate(content.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0xFF);
        buffer.put((byte) 0xFE);
        buffer.put(content);
        return buffer.array();
    }
}
