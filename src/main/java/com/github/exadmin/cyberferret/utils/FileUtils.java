package com.github.exadmin.cyberferret.utils;

import java.io.File;
import java.io.IOException;
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
        return new String(bytes);
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
}
