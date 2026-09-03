package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CyberFerretAppTests {
    @TempDir
    Path userHome;

    @Test
    void createsApplicationSettingsPathUnderUserHome() {
        Path settingsPath = CyberFerretApp.applicationPropertiesPath(userHome);

        assertEquals(userHome.resolve(".qubership").resolve("cyberferret.properties"), settingsPath);
        assertTrue(Files.isDirectory(settingsPath.getParent()));
    }
}
