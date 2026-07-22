package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CyberFerretCLITests {
    @TempDir
    Path tempDir;

    @Test
    public void loadStagedFiles_readsRelativeAndQuotedPaths() throws IOException {
        Path root = tempDir.resolve("repo");
        Files.createDirectories(root.resolve("sub/dir"));

        Path listFile = tempDir.resolve("staged.txt");
        String content = String.join("\n",
                "file1.txt",
                "   sub/dir/file2.txt   ",
                "\"file 3.txt\"",
                "",
                "   "
        );
        Files.writeString(listFile, content, StandardCharsets.UTF_8);

        List<Path> staged = CyberFerretCLI.loadStagedFiles(root, listFile);

        assertEquals(3, staged.size());
        assertEquals(root.resolve("file1.txt").normalize(), staged.get(0));
        assertEquals(root.resolve("sub/dir/file2.txt").normalize(), staged.get(1));
        assertEquals(root.resolve("file 3.txt").normalize(), staged.get(2));
    }

    @Test
    public void loadStagedFiles_keepsAbsolutePaths() throws IOException {
        Path root = tempDir.resolve("repo");
        Files.createDirectories(root);

        Path absoluteFile = tempDir.resolve("absolute.txt");
        Files.writeString(absoluteFile, "data", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("staged.txt");
        Files.writeString(listFile, absoluteFile.toString(), StandardCharsets.UTF_8);

        List<Path> staged = CyberFerretCLI.loadStagedFiles(root, listFile);

        assertEquals(1, staged.size());
        assertTrue(staged.get(0).isAbsolute());
        assertEquals(absoluteFile.normalize(), staged.get(0));
    }

    @Test
    public void loadFilesFromRepository_readsFilesRecursivelyAndSkipsGitDirectory() throws IOException {
        Path root = tempDir.resolve("repo");
        Files.createDirectories(root.resolve("sub"));
        Files.createDirectories(root.resolve(".git/objects"));
        Path rootFile = root.resolve("file1.txt");
        Path nestedFile = root.resolve("sub/file2.txt");
        Path gitFile = root.resolve(".git/objects/object1");
        Files.writeString(rootFile, "data", StandardCharsets.UTF_8);
        Files.writeString(nestedFile, "data", StandardCharsets.UTF_8);
        Files.writeString(gitFile, "data", StandardCharsets.UTF_8);

        List<Path> files = CyberFerretCLI.loadFilesFromRepository(root);

        assertEquals(2, files.size());
        assertTrue(files.contains(rootFile.normalize()));
        assertTrue(files.contains(nestedFile.normalize()));
    }

    @Test
    public void detailedScan_emptyStagedListDoesNotPrepareDictionary() throws IOException {
        Path root = tempDir.resolve("repo-empty-staged-list");
        Files.createDirectories(root);
        Path listFile = tempDir.resolve("staged-empty.txt");
        Files.writeString(listFile, "", StandardCharsets.UTF_8);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = CyberFerretCLI.run(
                new String[]{root.toString(), listFile.toString()},
                Map.of(AppConstants.SYS_ENV_VAR_PASSWORD, "wrong-password"),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(0, exitCode);
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }
}
