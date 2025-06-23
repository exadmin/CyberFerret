package com.github.exadmin.cyberferret.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
}
