package com.github.exadmin.cyberferret.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Loads and saves persistent settings for the JavaFX application.
 *
 * <p>The manager keeps UI state such as the window geometry, selected paths, and table column widths between
 * application runs. It loads registered properties from the path passed to the constructor and writes their latest
 * values to the same path when {@link #saveProperties()} is called.</p>
 *
 * <p>{@link com.github.exadmin.cyberferret.CyberFerretApp} stores application settings in
 * {@code .qubership/cyberferret.properties} under the user's home directory.</p>
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

    /**
     * Saves all registered properties without exposing a partially written target file.
     * I/O failures are logged and leave the application lifecycle running.
     */
    public void saveProperties() {
        Properties properties = new Properties();
        synchronized (REG_MAP) {
            for (Map.Entry<String, AbstractPersistentProperty<?>> me : REG_MAP.entrySet()) {
                Object value = me.getValue().getValue();
                if (value != null) properties.setProperty(me.getKey(), value.toString());
            }
        }
        try {
            storeAtomically(properties, filePath);
        } catch (IOException ex) {
            LOG.error("Error while saving application context properties into the file '{}'", filePath, ex);
        }
    }

    /**
     * Persists properties to a temporary sibling file and then replaces the target.
     * The temporary content is flushed to disk before the move, and unsupported atomic moves fall back to replacement.
     *
     * @param properties properties to persist
     * @param targetPath destination properties file
     * @throws IOException when writing, flushing, or replacing the target fails
     */
    static void storeAtomically(Properties properties, Path targetPath) throws IOException {
        Path target = targetPath.toAbsolutePath().normalize();
        Path temporary = Files.createTempFile(target.getParent(), "cyberferret-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, WRITE, TRUNCATE_EXISTING)) {
                OutputStream output = Channels.newOutputStream(channel);
                properties.store(output, "");
                output.flush();
                channel.force(true);
            }

            try {
                Files.move(temporary, target, REPLACE_EXISTING, ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
