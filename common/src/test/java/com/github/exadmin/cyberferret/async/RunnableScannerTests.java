package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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

    @Test
    public void cliMode_hidesMatchedValueWhenFindingsAreNotRevealed() throws IOException {
        Path repoRoot = tempDir.resolve("repo-safe-output");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);
        Path stagedFile = repoRoot.resolve("secret.txt");
        String sentinel = "DO_NOT_PRINT_THIS_SECRET";
        Files.writeString(stagedFile, sentinel, StandardCharsets.UTF_8);

        RunnableScanner scanner = scannerFor(repoRoot, stagedFile, Pattern.compile(sentinel));
        scanner.setRevealFindings(false);
        scanner.setPrintToConsole(true);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            scanner.run();
        } finally {
            System.setOut(originalOut);
        }

        assertTrue(scanner.isAnySignatureFound());
        assertFalse(captured.toString(StandardCharsets.UTF_8).contains(sentinel));
    }

    @Test
    public void cliMode_marksMissingSelectedFileAsOperationalFailure() throws IOException {
        Path repoRoot = tempDir.resolve("repo-missing-file");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);
        Path missingFile = repoRoot.resolve("missing.txt");

        RunnableScanner scanner = scannerFor(repoRoot, missingFile, Pattern.compile("secret"));
        scanner.run();

        assertTrue(scanner.hasOperationalFailure());
    }

    @Test
    public void cliMode_marksEmptySignatureMapAsOperationalFailure() throws IOException {
        Path repoRoot = tempDir.resolve("repo-empty-signatures");
        Files.createDirectories(repoRoot.resolve(".git"));
        Files.writeString(repoRoot.resolve(".git/config"), "[core]", StandardCharsets.UTF_8);

        RunnableScanner scanner = new RunnableScanner(true);
        scanner.setDirToScan(repoRoot.toString());
        scanner.setFoundItemsContainer(new FoundItemsContainer());
        scanner.setSignaturesMap(Map.of());
        scanner.setStagedFiles(List.of());
        scanner.run();

        assertTrue(scanner.hasOperationalFailure());
    }

    private static RunnableScanner scannerFor(Path repoRoot, Path stagedFile, Pattern pattern) {
        RunnableScanner scanner = new RunnableScanner(true);
        scanner.setDirToScan(repoRoot.toString());
        scanner.setFoundItemsContainer(new FoundItemsContainer());
        scanner.setSignaturesMap(Map.of("test", pattern));
        scanner.setAllowedSignaturesMap(Map.of());
        scanner.setExcludeExtMap(Map.of());
        scanner.setStagedFiles(List.of(stagedFile));
        return scanner;
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
