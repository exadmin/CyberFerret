package com.github.exadmin.cyberferret.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepositoryFilesTests {
    @TempDir
    Path tempDir;

    @Test
    public void load_keepsTrackedAndUntrackedFilesButSkipsStandardGitExclusions() throws Exception {
        Path repository = initializeRepository();
        Path trackedIgnoredByPattern = write(repository, "tracked.log");
        runGit(repository, "add", "tracked.log");

        Path untrackedSource = write(repository, "NewSource.java");
        Path gitIgnored = write(repository, "ignored.log");
        Path locallyExcluded = write(repository, "local.secret");
        Files.writeString(repository.resolve(".gitignore"), "*.log\n", StandardCharsets.UTF_8);
        Files.writeString(
                repository.resolve(".git/info/exclude"),
                "*.secret\n",
                StandardCharsets.UTF_8);

        RepositoryFileLoader loader = new RepositoryFileLoader();
        List<Path> files = loader.load(repository);

        assertTrue(loader.isGitRepository(repository));
        assertTrue(files.contains(trackedIgnoredByPattern));
        assertTrue(files.contains(untrackedSource));
        assertFalse(files.contains(gitIgnored));
        assertFalse(files.contains(locallyExcluded));
    }

    @Test
    public void load_honorsNegatedGitIgnoreRules() throws Exception {
        Path repository = initializeRepository();
        Files.createDirectories(repository.resolve("generated"));
        Path ignored = write(repository, "generated/ignored.txt");
        Path included = write(repository, "generated/included.txt");
        Files.writeString(
                repository.resolve(".gitignore"),
                "generated/*\n!generated/included.txt\n",
                StandardCharsets.UTF_8);

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertFalse(files.contains(ignored));
        assertTrue(files.contains(included));
    }

    @Test
    public void load_walksAllFilesOutsideGitRepositories() throws IOException {
        Path directory = tempDir.resolve("directory");
        Files.createDirectories(directory.resolve("nested"));
        Path rootFile = write(directory, "root.txt");
        Path nestedFile = write(directory, "nested/file.txt");

        RepositoryFileLoader loader = new RepositoryFileLoader();
        List<Path> files = loader.load(directory);

        assertFalse(loader.isGitRepository(directory));
        assertTrue(files.contains(rootFile));
        assertTrue(files.contains(nestedFile));
    }

    @Test
    public void load_doesNotMixSuccessfulGitWarningsIntoFilePaths() throws IOException {
        Path repository = tempDir.resolve("repository-with-warning");
        Files.createDirectories(repository.resolve(".git"));
        Files.writeString(repository.resolve(".git/config"), "[core]\n", StandardCharsets.UTF_8);
        Path sourceFile = write(repository, "Source.java");

        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
        RepositoryFileLoader loader = new RepositoryFileLoader(List.of(
                javaExecutable,
                "-cp",
                System.getProperty("java.class.path"),
                GitWithWarning.class.getName()));

        List<Path> files = loader.load(repository);

        assertTrue(files.contains(sourceFile));
    }

    @Test
    public void load_scansCheckedOutSubmodulesWithTheirOwnGitExclusions() throws Exception {
        Path submoduleSource = initializeRepository("submodule-source");
        write(submoduleSource, "Tracked.java");
        runGit(submoduleSource, "add", "Tracked.java");
        runGit(submoduleSource, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "init");

        Path repository = initializeRepository("parent-repository");
        runGit(
                repository,
                "-c",
                "protocol.file.allow=always",
                "submodule",
                "add",
                submoduleSource.toString(),
                "modules/library");

        Path submodule = repository.resolve("modules/library");
        Path trackedSource = submodule.resolve("Tracked.java").toAbsolutePath().normalize();
        Path untrackedSource = write(submodule, "NewSource.java");
        Path ignoredFile = write(submodule, "ignored.txt");
        Files.writeString(submodule.resolve(".gitignore"), "ignored.txt\n", StandardCharsets.UTF_8);

        List<Path> files = new RepositoryFileLoader().load(repository);

        assertTrue(files.contains(trackedSource));
        assertTrue(files.contains(untrackedSource));
        assertFalse(files.contains(ignoredFile));
    }

    private Path initializeRepository() throws Exception {
        return initializeRepository("repository");
    }

    private Path initializeRepository(String directoryName) throws Exception {
        Path repository = tempDir.resolve(directoryName);
        Files.createDirectories(repository);
        runGit(repository, "init");
        return repository;
    }

    private static Path write(Path root, String relativePath) throws IOException {
        Path path = root.resolve(relativePath);
        Files.writeString(path, "content", StandardCharsets.UTF_8);
        return path.toAbsolutePath().normalize();
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

    public static class GitWithWarning {
        public static void main(String[] arguments) throws Exception {
            System.err.println("Git warning");
            System.err.flush();
            Thread.sleep(50);
            System.out.write("Source.java\0".getBytes(StandardCharsets.UTF_8));
        }
    }
}
