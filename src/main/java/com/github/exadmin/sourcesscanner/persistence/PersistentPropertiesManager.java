package com.github.exadmin.sourcesscanner.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PersistentPropertiesManager {
    private static final Map<String, AbstractPersistentProperty<?>> REG_MAP = new HashMap<>();
    public static final AbstractPersistentProperty<Double>  STAGE_WIDTH         = new AppDoubleProperty("stage.width", 640d, REG_MAP);
    public static final AbstractPersistentProperty<Double>  STAGE_HEIGHT        = new AppDoubleProperty("stage.height", 480d, REG_MAP);
    public static final AbstractPersistentProperty<Double>  STAGE_POSX          = new AppDoubleProperty("stage.posX", 0d, REG_MAP);
    public static final AbstractPersistentProperty<Double>  STAGE_POSY          = new AppDoubleProperty("stage.posY", 0d, REG_MAP);
    public static final AbstractPersistentProperty<String>  DICTIONARY          = new AppStringProperty("dictionary", "", REG_MAP);
    public static final AbstractPersistentProperty<String>  DIR_TO_SCAN          = new AppStringProperty("dir-to-scan", "", REG_MAP);
    public static final AbstractPersistentProperty<Boolean> STAGE_IS_MAXIMIZED  = new AppBooleanProperty("stage.maximized", false, REG_MAP);

    private static final Logger log = LoggerFactory.getLogger(PersistentPropertiesManager.class);
    private Path filePath;

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
                AbstractPersistentProperty<?> pProperty = REG_MAP.get(strKey);
                if (pProperty == null) {
                    log.warn("Unknown key in the persisten properties list '{}'", strKey);
                    continue;
                }

                String strValue = properties.getProperty(strKey);
                pProperty.parseValue(strValue);
            }
        } catch (FileNotFoundException fnfe) {
            log.warn("Application context file '{}' was not found. Context will be initiated with default values.", filePath);
        } catch (IOException ex) {
            log.error("Error while loading application context file '{}'", filePath, ex);
        }
    }

    public void saveProperties() {
        Properties properties = new Properties();
        for (Map.Entry<String, AbstractPersistentProperty<?>> me : REG_MAP.entrySet()) {
            Object value = me.getValue().getValue();
            if (value != null) properties.setProperty(me.getKey(), value.toString());
        }
        try (OutputStream os = new FileOutputStream(filePath.toFile())) {
            properties.store(os, "");
        } catch (IOException ex) {
            log.error("Error while saving application context properties into the file '{}'", filePath, ex);
        }
    }
}
