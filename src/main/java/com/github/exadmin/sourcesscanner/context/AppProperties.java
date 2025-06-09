package com.github.exadmin.sourcesscanner.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public class AppProperties {
    private static final Logger log = LoggerFactory.getLogger(AppProperties.class);
    private Properties properties;
    private Path filePath;

    public AppProperties(Path persistenFilePath) {
        this.filePath = persistenFilePath;
        loadProperties();
    }

    public <T> void setValue(AppAbstractProperty<T> property, T value) {
        properties.setProperty(property.toString(), value + "");
    }

    public <T> T getValue(AppAbstractProperty<T> property) {
        String strValue = properties.getProperty(property.toString());
        if (strValue == null) return property.getDefaultValue();

        try {
            return property.parseValue(strValue);
        } catch (NumberFormatException nfe) {
            log.warn("Error while parsing key {} with value {}", property, strValue, nfe);
        }

        return property.getDefaultValue();
    }

    protected void loadProperties() {
        properties = new Properties();

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            properties.load(fis);
        } catch (FileNotFoundException fnfe) {
            log.warn("Application context file '{}' was not found. Context will be initiated with default values.", filePath);
        } catch (IOException ex) {
            log.error("Error while loading application context file '{}'", filePath, ex);
        }
    }

    public void saveProperties() {
        try (OutputStream os = new FileOutputStream(filePath.toFile())) {
            properties.store(os, "");
        } catch (IOException ex) {
            log.error("Error while saving application context properties into the file '{}'", filePath, ex);
        }
    }
}
