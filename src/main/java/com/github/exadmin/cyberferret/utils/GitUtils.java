package com.github.exadmin.cyberferret.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Examples:
 * String hooks = GitUtils.getGlobalConfigValue("core.hooksPath");
 * String name = GitUtils.getGlobalConfigValue("user.name");
 * String email = GitUtils.getGlobalConfigValue("user.email");
 */
public class GitUtils {

    public static String getGlobalConfigValue(String key) {
        if (key == null || !key.contains(".")) {
            throw new IllegalArgumentException("Key must have format 'section.property'");
        }

        String section = key.substring(0, key.indexOf('.'));
        String property = key.substring(key.indexOf('.') + 1);

        Path config = findGlobalGitConfig();
        if (config == null) {
            return null;
        }

        String currentSection = null;

        try (BufferedReader reader = Files.newBufferedReader(config)) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim();
                    continue;
                }

                if (!section.equalsIgnoreCase(currentSection)) {
                    continue;
                }

                int idx = line.indexOf('=');
                if (idx < 0) {
                    continue;
                }

                String keyName = line.substring(0, idx).trim();
                if (!property.equalsIgnoreCase(keyName)) {
                    continue;
                }

                return line.substring(idx + 1).trim();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read git global config: " + config, e);
        }

        return null;
    }

    private static Path findGlobalGitConfig() {

        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            Path p = Path.of(xdg, "git", "config");
            if (Files.exists(p)) {
                return p;
            }
        }

        Path home = Path.of(System.getProperty("user.home"));

        Path xdgDefault = home.resolve(".config").resolve("git").resolve("config");
        if (Files.exists(xdgDefault)) {
            return xdgDefault;
        }

        Path classic = home.resolve(".gitconfig");
        if (Files.exists(classic)) {
            return classic;
        }

        return null;
    }
}