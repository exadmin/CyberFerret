package com.github.exadmin.cyberferret.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

public class FileUtils {
    public static String readFile(String filePath) throws IOException {
        return readFile(Paths.get(filePath));
    }

    public static String readFile(Path filePath) throws IOException {
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

    /**
     * Detects if a file is binary by checking if the first 16KiB contains any zero bytes.
     * @param path the file path to check
     * @return true if the file is detected as binary, false otherwise
     * @throws IOException if an I/O error occurs
     */
    public static boolean isBinaryFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        // Read first 16KiB
        byte[] buffer = new byte[16 * 1024];
        try (InputStream is = Files.newInputStream(path)) {
            int bytesRead = is.read(buffer);
            if (bytesRead > 0) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == 0) {
                        return true; // Found zero byte, file is binary
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if a file name matches any of the provided patterns.
     * @param path the file path to check
     * @param patterns list of regex patterns to match against the file name
     * @return true if the file name matches any pattern, false otherwise
     */
    public static boolean matchesAnyPattern(Path path, List<Pattern> patterns) {
        if (path == null || patterns == null || patterns.isEmpty()) {
            return false;
        }

        String fileName = path.getFileName().toString();
        for (Pattern pattern : patterns) {
            if (pattern.matcher(fileName).matches()) {
                return true;
            }
        }
        return false;
    }
}
