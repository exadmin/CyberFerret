package com.github.exadmin.cyberferret;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void persistsOnlyNormalStageGeometryChanges() {
        SimpleBooleanProperty maximized = new SimpleBooleanProperty(false);
        SimpleDoubleProperty width = new SimpleDoubleProperty(640);
        AtomicReference<Number> persistedWidth = new AtomicReference<>(640);
        width.addListener(CyberFerretApp.normalStageGeometryListener(maximized::get, persistedWidth::set));

        width.set(800);
        assertEquals(800.0, persistedWidth.get());

        maximized.set(true);
        width.set(1920);
        assertEquals(800.0, persistedWidth.get());
    }
}
