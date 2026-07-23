package com.github.exadmin.cyberferret.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {
    public static String readFile(String filePath) throws IOException {
        return readFile(Paths.get(filePath));
    }

    public static String readFile(Path filePath) throws IOException {
        // Check if this is an image file that should have metadata extracted
        String extension = getFileExtensionAsString(filePath);
        if (ImgUtils.isSupportedImageFormat(extension)) {
            // Extract and return metadata as searchable text
            String metadata = ImgUtils.extractMetadataAsText(filePath);

            // If metadata extraction succeeded, return it
            // Otherwise fall back to reading file as text (empty metadata means extraction failed)
            if (!metadata.isEmpty()) {
                return metadata;
            }
        }

        // For non-image files or if metadata extraction failed, read as text
        byte[] bytes = Files.readAllBytes(filePath);
        DecodedText decodedText = decodeText(bytes);
        return new String(bytes, decodedText.offset(), bytes.length - decodedText.offset(), decodedText.charset());
    }

    public static void saveToFile(String content, String fileToWriteInto) throws IOException {
        Path path = Paths.get(fileToWriteInto);
        Files.write(path, content.getBytes());
    }

    public static String getFileExtensionAsString(Path path) {
        if (path == null) return null;

        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        return (dotIndex > 0 && dotIndex < fileName.length() - 1)
                ? fileName.substring(dotIndex + 1)
                : null;
    }

    public static boolean isPathToDir(Path path) {
        File file = path.toFile();
        return (file.isDirectory());
    }

    public static InputStream toFileInputStream(Path path) {
        try {
            return Files.newInputStream(path);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static DecodedText decodeText(byte[] bytes) {
        if (startsWith(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new DecodedText(StandardCharsets.UTF_8, 3);
        }
        if (startsWith(bytes, (byte) 0xFF, (byte) 0xFE)) {
            return new DecodedText(StandardCharsets.UTF_16LE, 2);
        }
        if (startsWith(bytes, (byte) 0xFE, (byte) 0xFF)) {
            return new DecodedText(StandardCharsets.UTF_16BE, 2);
        }

        if (looksLikeUtf16Le(bytes)) {
            return new DecodedText(StandardCharsets.UTF_16LE, 0);
        }
        if (looksLikeUtf16Be(bytes)) {
            return new DecodedText(StandardCharsets.UTF_16BE, 0);
        }

        return new DecodedText(StandardCharsets.UTF_8, 0);
    }

    private static boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeUtf16Le(byte[] bytes) {
        if (bytes.length < 2 || bytes.length % 2 != 0) {
            return false;
        }

        int zeroBytesOnOddIndexes = 0;
        for (int i = 1; i < bytes.length; i += 2) {
            if (bytes[i] == 0) {
                zeroBytesOnOddIndexes++;
            }
        }
        return zeroBytesOnOddIndexes * 2 >= bytes.length - 2;
    }

    private static boolean looksLikeUtf16Be(byte[] bytes) {
        if (bytes.length < 2 || bytes.length % 2 != 0) {
            return false;
        }

        int zeroBytesOnEvenIndexes = 0;
        for (int i = 0; i < bytes.length; i += 2) {
            if (bytes[i] == 0) {
                zeroBytesOnEvenIndexes++;
            }
        }
        return zeroBytesOnEvenIndexes * 2 >= bytes.length - 2;
    }

    private record DecodedText(Charset charset, int offset) {
    }
}
