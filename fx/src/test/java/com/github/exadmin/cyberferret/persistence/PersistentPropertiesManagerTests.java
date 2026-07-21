package com.github.exadmin.cyberferret.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
