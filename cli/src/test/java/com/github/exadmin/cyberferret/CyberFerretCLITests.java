package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
