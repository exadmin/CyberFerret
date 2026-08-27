package com.github.exadmin.cyberferret.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads and saves persistent settings for the JavaFX application.
 *
 * <p>The manager keeps UI state such as the window geometry, selected paths, and table column widths between
 * application runs. It loads registered properties from the path passed to the constructor and writes their latest
 * values to the same path when {@link #saveProperties()} is called.</p>
 *
 * <p>{@link com.github.exadmin.cyberferret.CyberFerretApp} configures this manager with {@code app.properties}, so the
 * application stores its settings in that file in the process working directory.</p>
 */
public class PersistentPropertiesManager {
    private static final Map<String, AbstractPersistentProperty<?>> REG_MAP = Collections.synchronizedMap(new HashMap<>());
    public static final AbstractPersistentProperty<Number> STAGE_WIDTH = new AppDoubleProperty("stage.width", 640d, REG_MAP);
    public static final AbstractPersistentProperty<Number> STAGE_HEIGHT = new AppDoubleProperty("stage.height", 480d, REG_MAP);
    public static final AbstractPersistentProperty<Number> STAGE_POSX = new AppDoubleProperty("stage.posX", 0d, REG_MAP);
    public static final AbstractPersistentProperty<Number> STAGE_POSY = new AppDoubleProperty("stage.posY", 0d, REG_MAP);
    public static final AbstractPersistentProperty<String> DIR_TO_SCAN = new AppStringProperty("dir-to-scan", "", REG_MAP);
    public static final AbstractPersistentProperty<String> CF_CLI_PATH =
            new AppStringProperty("cfcli.path", "", REG_MAP);
    public static final AbstractPersistentProperty<Boolean> STAGE_IS_MAXIMIZED = new AppBooleanProperty("stage.maximized", false, REG_MAP);
    public static final AbstractPersistentProperty<Number> PATH_NAME_COLUMN_WIDTH =
            new AppDoubleProperty("tree-table.path-name-column.width", 200d, REG_MAP);
    public static final AbstractPersistentProperty<Number> STATUS_COLUMN_WIDTH =
            new AppDoubleProperty("tree-table.status-column.width", 80d, REG_MAP);
    public static final AbstractPersistentProperty<Number> LINE_COLUMN_WIDTH =
            new AppDoubleProperty("tree-table.line-column.width", 80d, REG_MAP);
    public static final AbstractPersistentProperty<Number> EXACT_SIGNATURE_COLUMN_WIDTH =
            new AppDoubleProperty("tree-table.exact-signature-column.width", 200d, REG_MAP);
    public static final AbstractPersistentProperty<Number> FOUND_TEXT_COLUMN_WIDTH =
            new AppDoubleProperty("tree-table.found-text-column.width", 80d, REG_MAP);

    private static final Logger LOG = LoggerFactory.getLogger(PersistentPropertiesManager.class);
    private final Path filePath;

    public PersistentPropertiesManager(Path persistenFilePath) {
        this.filePath = persistenFilePath;
        loadProperties();
    }

    protected void loadProperties() {
        Properties properties = new Properties();

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            properties.load(fis);

            for (Object key : properties.keySet()) {
                String strKey = key.toString();
                AbstractPersistentProperty<?> pProperty;
                synchronized (REG_MAP) {
                    pProperty = REG_MAP.get(strKey);
                }
                if (pProperty == null) {
                    LOG.warn("Unknown key in the persistent properties list '{}'", strKey);
                    continue;
                }

                String strValue = properties.getProperty(strKey);
                pProperty.parseValue(strValue);
            }
        } catch (FileNotFoundException fnfe) {
            LOG.warn("Application context file '{}' was not found. Context will be initiated with default values.", filePath);
        } catch (IOException ex) {
            LOG.error("Error while loading application context file '{}'", filePath, ex);
        }
    }

    public void saveProperties() {
        Properties properties = new Properties();
        synchronized (REG_MAP) {
            for (Map.Entry<String, AbstractPersistentProperty<?>> me : REG_MAP.entrySet()) {
                Object value = me.getValue().getValue();
                if (value != null) properties.setProperty(me.getKey(), value.toString());
            }
        }
        try (OutputStream os = new FileOutputStream(filePath.toFile())) {
            properties.store(os, "");
        } catch (IOException ex) {
            LOG.error("Error while saving application context properties into the file '{}'", filePath, ex);
        }
    }
}
