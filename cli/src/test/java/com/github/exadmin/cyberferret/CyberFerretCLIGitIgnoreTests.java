package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CyberFerretCLIGitIgnoreTests {
    @TempDir
    Path tempDir;

    @Test
    public void loadFilesFromRepository_skipsGitIgnoredFilesButKeepsUntrackedFiles() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository);
        runGit(repository, "init");
        Path emptyExcludesFile = tempDir.resolve("empty-global-excludes");
        Files.writeString(emptyExcludesFile, "", StandardCharsets.UTF_8);
        runGit(repository, "config", "core.excludesFile", emptyExcludesFile.toString());

        Path untrackedSource = repository.resolve("NewSource.java");
        Path gitIgnored = repository.resolve("ignored.txt");
        Path locallyExcluded = repository.resolve("local.secret");
        Files.writeString(untrackedSource, "content", StandardCharsets.UTF_8);
        Files.writeString(gitIgnored, "content", StandardCharsets.UTF_8);
        Files.writeString(locallyExcluded, "content", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve(".gitignore"), "ignored.txt\n", StandardCharsets.UTF_8);
        Files.writeString(
                repository.resolve(".git/info/exclude"),
                "*.secret\n",
                StandardCharsets.UTF_8);

        List<Path> files = CyberFerretCLI.loadFilesFromRepository(repository);

        assertTrue(files.contains(untrackedSource.toAbsolutePath().normalize()));
        assertFalse(files.contains(gitIgnored.toAbsolutePath().normalize()));
        assertFalse(files.contains(locallyExcluded.toAbsolutePath().normalize()));
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
