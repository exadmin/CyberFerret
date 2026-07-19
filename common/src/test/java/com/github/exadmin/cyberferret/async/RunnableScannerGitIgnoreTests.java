package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class RunnableScannerGitIgnoreTests {
    @TempDir
    Path tempDir;

    @Test
    public void guiMode_scansUntrackedFilesButSkipsGitIgnoredFiles() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository);
        runGit(repository, "init");
        Path emptyExcludesFile = tempDir.resolve("empty-global-excludes");
        Files.writeString(emptyExcludesFile, "", StandardCharsets.UTF_8);
        runGit(repository, "config", "core.excludesFile", emptyExcludesFile.toString());

        Path untrackedSource = repository.resolve("NewSource.java");
        Path gitIgnored = repository.resolve("ignored.txt");
        Path locallyExcluded = repository.resolve("local.secret");
        Files.writeString(untrackedSource, "secret", StandardCharsets.UTF_8);
        Files.writeString(gitIgnored, "secret", StandardCharsets.UTF_8);
        Files.writeString(locallyExcluded, "secret", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve(".gitignore"), "ignored.txt\n", StandardCharsets.UTF_8);
        Files.writeString(
                repository.resolve(".git/info/exclude"),
                "*.secret\n",
                StandardCharsets.UTF_8);

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner scanner = new RunnableScanner(false);
        scanner.setDirToScan(repository.toString());
        scanner.setFoundItemsContainer(foundItemsContainer);
        scanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        scanner.run();

        Set<Path> scannedFiles = foundItemsContainer.getFoundItemsCopy().stream()
                .filter(item -> item.getType() == ItemType.FILE)
                .map(FoundPathItem::getFilePath)
                .collect(Collectors.toSet());
        assertTrue(scannedFiles.contains(untrackedSource.toAbsolutePath().normalize()));
        assertFalse(scannedFiles.contains(gitIgnored.toAbsolutePath().normalize()));
        assertFalse(scannedFiles.contains(locallyExcluded.toAbsolutePath().normalize()));
        assertTrue(scanner.isAnySignatureFound());
    }

    @Test
    public void guiMode_keepsEmptyDirectoriesOutsideGitRepositories() throws Exception {
        Path directory = tempDir.resolve("empty-directory");
        Files.createDirectories(directory);

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner scanner = createScanner(directory, foundItemsContainer);

        scanner.run();

        assertEquals(1, foundItemsContainer.getFoundItemsSize());
        FoundPathItem rootItem = foundItemsContainer.getFoundItemsCopy().getFirst();
        assertEquals(ItemType.DIRECTORY, rootItem.getType());
        assertEquals(directory.toAbsolutePath().normalize(), rootItem.getFilePath());
    }

    @Test
    public void guiMode_keepsSymbolicLinksOutsideGitRepositories() throws Exception {
        Path directory = tempDir.resolve("directory-with-link");
        Files.createDirectories(directory);
        Path target = directory.resolve("target.txt");
        Path symbolicLink = directory.resolve("link.txt");
        Files.writeString(target, "content", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(symbolicLink, target.getFileName());
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            assumeTrue(false, "Symbolic links are not available: " + ex.getMessage());
        }

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner scanner = createScanner(directory, foundItemsContainer);

        scanner.run();

        Set<Path> scannedFiles = foundItemsContainer.getFoundItemsCopy().stream()
                .filter(item -> item.getType() == ItemType.FILE)
                .map(FoundPathItem::getFilePath)
                .collect(Collectors.toSet());
        assertTrue(scannedFiles.contains(symbolicLink.toAbsolutePath().normalize()));
    }

    private static RunnableScanner createScanner(
            Path directory,
            FoundItemsContainer foundItemsContainer) {
        RunnableScanner scanner = new RunnableScanner(false);
        scanner.setDirToScan(directory.toString());
        scanner.setFoundItemsContainer(foundItemsContainer);
        scanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        return scanner;
    }

    private static void runGit(Path repository, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = repository.toString();
        System.arraycopy(arguments, 0, command, 3, arguments.length);

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Git command failed: " + output);
        }
    }
}
