package com.github.exadmin.cyberferret.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersistentPropertiesManagerTests {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsAndSavesCfCliPathWithoutPersistingPassword() throws IOException {
        Path propertiesFile = tempDirectory.resolve("app.properties");
        Properties input = new Properties();
        input.setProperty("cfcli.path", "C:\\Tools\\cfcli.exe");
        input.setProperty("dictionary.password", "must-not-survive");
        try (OutputStream output = Files.newOutputStream(propertiesFile)) {
            input.store(output, "");
        }

        PersistentPropertiesManager manager = new PersistentPropertiesManager(propertiesFile);

        assertEquals("C:\\Tools\\cfcli.exe", PersistentPropertiesManager.CF_CLI_PATH.getValue());
        manager.saveProperties();

        Properties saved = new Properties();
        try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
            saved.load(inputStream);
        }
        assertEquals("C:\\Tools\\cfcli.exe", saved.getProperty("cfcli.path"));
        assertFalse(saved.containsKey("go-cli.path"));
        assertFalse(saved.containsKey("dictionary.password"));
    }

    @Test
    void atomicallyReplacesExistingFileAndRemovesTemporaryFile() throws IOException {
        Path propertiesFile = tempDirectory.resolve("cyberferret.properties");
        Files.writeString(propertiesFile, "obsolete=true");
        Properties replacement = new Properties();
        replacement.setProperty("stage.width", "800.0");

        PersistentPropertiesManager.storeAtomically(replacement, propertiesFile);

        Properties saved = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            saved.load(input);
        }
        assertEquals("800.0", saved.getProperty("stage.width"));
        assertFalse(saved.containsKey("obsolete"));
        try (Stream<Path> entries = Files.list(tempDirectory)) {
            assertEquals(List.of(propertiesFile), entries.toList());
        }
    }
}
