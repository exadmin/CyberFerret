package com.github.exadmin.cyberferret.utils;

import com.github.exadmin.cyberferret.async.RunnableScanner;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class RepositoryFileLoaderEdgeCaseTests {
    @TempDir
    Path tempDir;

    @Test
    public void load_doesNotRecurseIntoTrackedSymlinkToExternalGitRepository() throws Exception {
        Path externalRepository = initializeRepository("external-repository");
        Files.writeString(
                externalRepository.resolve("external-secret.txt"),
                "secret",
                StandardCharsets.UTF_8);

        Path repository = initializeRepository("repository");
        Path linkedRepository = repository.resolve("linked-repository");
        try {
            Files.createSymbolicLink(linkedRepository, externalRepository);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            assumeTrue(false, "Symbolic links are not available: " + ex.getMessage());
        }
        runGit(repository, "add", "linked-repository");

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertFalse(files.stream().anyMatch(path -> path.startsWith(linkedRepository)));
    }

    @Test
    public void load_keepsTrackedSymlinkToRegularFile() throws Exception {
        Path externalFile = tempDir.resolve("external-tracked.txt");
        Files.writeString(externalFile, "secret", StandardCharsets.UTF_8);
        Path repository = initializeRepository("tracked-file-symlink");
        Path symbolicLink = createSymbolicLink(repository.resolve("linked.txt"), externalFile);
        runGit(repository, "add", "linked.txt");

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertTrue(files.contains(symbolicLink.toAbsolutePath().normalize()));
    }

    @Test
    public void load_keepsUntrackedSymlinkToRegularFile() throws Exception {
        Path externalFile = tempDir.resolve("external-untracked.txt");
        Files.writeString(externalFile, "secret", StandardCharsets.UTF_8);
        Path repository = initializeRepository("untracked-file-symlink");
        Path symbolicLink = createSymbolicLink(repository.resolve("linked.txt"), externalFile);

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertTrue(files.contains(symbolicLink.toAbsolutePath().normalize()));
    }

    @Test
    public void load_keepsTrackedUnixPathWithInvalidUtf8Bytes() throws Exception {
        assumeUnixFileNames();
        Path repository = initializeRepository("tracked-invalid-utf8");
        runShell(
                repository,
                "name=$(printf 'tracked-\\377.txt'); printf 'secret' > \"$name\"; git add -- \"$name\"");
        Path invalidUtf8File = findInvalidUtf8Path(repository);

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertTrue(files.contains(invalidUtf8File));
    }

    @Test
    public void load_keepsUntrackedUnixPathWithInvalidUtf8Bytes() throws Exception {
        assumeUnixFileNames();
        Path repository = initializeRepository("untracked-invalid-utf8");
        runShell(repository, "name=$(printf 'untracked-\\377.txt'); printf 'secret' > \"$name\"");
        Path invalidUtf8File = findInvalidUtf8Path(repository);

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertTrue(files.contains(invalidUtf8File));
    }

    @Test
    public void load_keepsUntrackedUnixPathContainingTab() throws Exception {
        assumeUnixFileNames();
        Path repository = initializeRepository("untracked-tab");
        Path file = repository.resolve("before\tafter.txt");
        Path ignoredSuffix = repository.resolve("after.txt");
        Files.writeString(file, "secret", StandardCharsets.UTF_8);
        Files.writeString(ignoredSuffix, "secret", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve(".gitignore"), "after.txt\n", StandardCharsets.UTF_8);

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertTrue(files.contains(file.toAbsolutePath().normalize()));
        assertFalse(files.contains(ignoredSuffix.toAbsolutePath().normalize()));
    }

    @Test
    public void load_skipsIgnoredUnixPathWithInvalidUtf8Bytes() throws Exception {
        assumeUnixFileNames();
        Path repository = initializeRepository("ignored-invalid-utf8");
        runShell(
                repository,
                "name=$(printf 'ignored-\\377.txt'); printf 'secret' > \"$name\"; "
                        + "printf '%s\\n' \"$name\" > .gitignore");
        Path invalidUtf8File = findInvalidUtf8Path(repository);

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertFalse(files.contains(invalidUtf8File));
    }

    @Test
    public void guiMode_scansTrackedUnixPathWithInvalidUtf8Bytes() throws Exception {
        assumeUnixFileNames();
        Path repository = initializeRepository("scanner-invalid-utf8");
        runShell(
                repository,
                "name=$(printf 'tracked-\\377.txt'); printf 'secret' > \"$name\"; git add -- \"$name\"");

        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();
        RunnableScanner scanner = new RunnableScanner(false);
        scanner.setDirToScan(repository.toString());
        scanner.setFoundItemsContainer(foundItemsContainer);
        scanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));

        scanner.run();

        assertTrue(scanner.isAnySignatureFound());
    }

    private Path initializeRepository(String directoryName) throws Exception {
        Path repository = tempDir.resolve(directoryName);
        Files.createDirectories(repository);
        runGit(repository, "init");
        return repository;
    }

    private static void assumeUnixFileNames() {
        assumeFalse(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"),
                "Invalid UTF-8 filename bytes are a Unix-specific case");
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "/bin/sh is required for this Unix-specific test");
    }

    private static Path findInvalidUtf8Path(Path repository) throws IOException {
        try (Stream<Path> paths = Files.list(repository)) {
            return paths
                    .filter(path -> path.toUri().getRawPath().toUpperCase(Locale.ROOT).contains("%FF"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Cannot find the invalid UTF-8 test path"));
        }
    }

    private static Path createSymbolicLink(Path link, Path target) {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            assumeTrue(false, "Symbolic links are not available: " + ex.getMessage());
            throw new IllegalStateException(ex);
        }
    }

    private static void runShell(Path directory, String script) throws Exception {
        Process process = new ProcessBuilder("/bin/sh", "-c", script)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Shell command failed: " + output);
        }
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
