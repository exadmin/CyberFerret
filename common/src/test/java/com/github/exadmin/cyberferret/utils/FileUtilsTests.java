package com.github.exadmin.cyberferret.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilsTests {
    // PNG magic header: a real binary signature that is not valid text in any encoding
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};

    @TempDir
    Path tempDir;

    @Test
    public void isBinaryFile_detectsNulByteAsBinary() throws IOException {
        Path file = tempDir.resolve("blob.bin");
        Files.write(file, PNG_HEADER);

        assertTrue(FileUtils.isBinaryFile(file));
    }

    @Test
    public void isBinaryFile_treatsPlainTextAsText() throws IOException {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "just some text", StandardCharsets.UTF_8);

        assertFalse(FileUtils.isBinaryFile(file));
    }

    @Test
    public void isBinaryFile_treatsUtf16TextAsText() throws IOException {
        // UTF-16 contains NUL bytes by design, yet it is still text and must be scanned
        Path file = tempDir.resolve("notes-utf16.txt");
        Files.write(file, utf16LeWithBom("hack hack\r\n"));

        assertFalse(FileUtils.isBinaryFile(file));
    }

    @Test
    public void isBinaryFile_treatsEmptyFileAsText() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.write(file, new byte[0]);

        assertFalse(FileUtils.isBinaryFile(file));
    }

    @Test
    public void isBinaryFile_returnsFalseForDirectory() throws IOException {
        assertFalse(FileUtils.isBinaryFile(tempDir));
    }

    @Test
    public void matchesAnyPattern_matchesByFileName() {
        Path file = Path.of("lib", "app.jar");
        List<Pattern> patterns = List.of(Pattern.compile(".*\\.png"), Pattern.compile(".*\\.jar"));

        assertTrue(FileUtils.matchesAnyPattern(file, patterns));
    }

    @Test
    public void matchesAnyPattern_returnsFalseWhenNothingMatches() {
        Path file = Path.of("lib", "app.jar");
        List<Pattern> patterns = List.of(Pattern.compile(".*\\.png"));

        assertFalse(FileUtils.matchesAnyPattern(file, patterns));
    }

    @Test
    public void matchesAnyPattern_returnsFalseForEmptyOrNullPatterns() {
        Path file = Path.of("app.jar");

        assertFalse(FileUtils.matchesAnyPattern(file, List.of()));
        assertFalse(FileUtils.matchesAnyPattern(file, null));
    }

    @Test
    public void matchesAnyPattern_whitelistsWrapperJarsButNotOtherJars() {
        // the default binary-exclude patterns shipped in README.md
        List<Pattern> defaults = List.of(Pattern.compile("gradle-wrapper\\.jar"), Pattern.compile("maven-wrapper\\.jar"));

        assertTrue(FileUtils.matchesAnyPattern(Path.of("gradle", "wrapper", "gradle-wrapper.jar"), defaults));
        assertTrue(FileUtils.matchesAnyPattern(Path.of(".mvn", "wrapper", "maven-wrapper.jar"), defaults));
        assertFalse(FileUtils.matchesAnyPattern(Path.of("build", "libs", "app.jar"), defaults));
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
