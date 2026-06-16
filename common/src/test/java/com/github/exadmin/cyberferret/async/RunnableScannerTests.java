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
    // PNG magic header: a real binary signature used as a stand-in for a committed binary file
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};

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

    @Test
    public void cliMode_flagsBinaryFileAsArtifact() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);

        Path stagedFile = repoRoot.resolve("blob.bin");
        Files.write(stagedFile, PNG_HEADER);

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setDirToScan(repoRoot.toString());
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        runnableScanner.setAllowedSignaturesMap(Map.of());
        runnableScanner.setExcludeExtMap(Map.of());
        runnableScanner.setStagedFiles(List.of(stagedFile));

        runnableScanner.run();

        List<FoundPathItem> artifacts = foundItemsContainer.getFoundItemsCopy().stream()
                .filter(item -> "BINARY_ARTIFACT".equals(item.getVisualName()))
                .toList();
        assertEquals(1, artifacts.size());
        assertEquals(stagedFile.toAbsolutePath().normalize(), artifacts.getFirst().getFilePath());
        // a binary artifact must fail the scan so the CLI/pre-commit hook exits non-zero
        assertTrue(runnableScanner.isAnySignatureFound());
    }

    @Test
    public void cliMode_skipsBinaryFileMatchingExcludePattern() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);

        Path stagedFile = repoRoot.resolve("archive.dat");
        Files.write(stagedFile, PNG_HEADER);

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setDirToScan(repoRoot.toString());
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        runnableScanner.setAllowedSignaturesMap(Map.of());
        runnableScanner.setExcludeExtMap(Map.of());
        runnableScanner.setBinaryExcludePatterns(List.of(Pattern.compile(".*\\.dat")));
        runnableScanner.setStagedFiles(List.of(stagedFile));

        runnableScanner.run();

        boolean anyArtifact = foundItemsContainer.getFoundItemsCopy().stream()
                .anyMatch(item -> "BINARY_ARTIFACT".equals(item.getVisualName()));
        assertFalse(anyArtifact);
        assertFalse(runnableScanner.isAnySignatureFound());
    }

    @Test
    public void cliMode_doesNotFlagSupportedImageAsBinaryArtifact() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);

        // images are binary, but their metadata is scanned for signatures, so they must not be flagged
        Path stagedFile = repoRoot.resolve("logo.png");
        Files.write(stagedFile, PNG_HEADER);

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setDirToScan(repoRoot.toString());
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        runnableScanner.setAllowedSignaturesMap(Map.of());
        runnableScanner.setExcludeExtMap(Map.of());
        runnableScanner.setStagedFiles(List.of(stagedFile));

        runnableScanner.run();

        boolean anyArtifact = foundItemsContainer.getFoundItemsCopy().stream()
                .anyMatch(item -> "BINARY_ARTIFACT".equals(item.getVisualName()));
        assertFalse(anyArtifact);
    }

    @Test
    public void cliMode_handlesAbsoluteStagedFileWhenScanDirectoryIsRelative() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);
        Path stagedFile = repoRoot.resolve("staged.txt");
        Files.writeString(stagedFile, "clean", StandardCharsets.UTF_8);
        Path relativeRepoRoot = Path.of("").toAbsolutePath().normalize()
                .relativize(repoRoot.toAbsolutePath().normalize());

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setDirToScan(relativeRepoRoot.toString());
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        runnableScanner.setAllowedSignaturesMap(Map.of());
        runnableScanner.setExcludeExtMap(Map.of());
        runnableScanner.setStagedFiles(List.of(stagedFile.toAbsolutePath().normalize()));

        runnableScanner.run();

        List<FoundPathItem> foundItems = foundItemsContainer.getFoundItemsCopy();
        assertEquals(1, foundItems.size());
        assertEquals(stagedFile.toAbsolutePath().normalize(), foundItems.getFirst().getFilePath());
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
