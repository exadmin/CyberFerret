package com.github.exadmin.cyberferret.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MiscUtils {
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    private static final String VERSION_PROPERTIES_RESOURCE = "/version.properties";

    public static String loadApplicationVersion() {
        Properties props = new Properties();
        try (InputStream input = MiscUtils.class.getResourceAsStream(VERSION_PROPERTIES_RESOURCE)) {
            if (input == null) {
                // log.error("Resource {} is not found on the classpath", VERSION_PROPERTIES_RESOURCE);
                throw new RuntimeException("Resource '" + VERSION_PROPERTIES_RESOURCE + "' not found");
            }
            props.load(input);
            return props.getProperty("application.version");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
            // log.error("Error while loading {}", VERSION_PROPERTIES_RESOURCE, ex);
        }

        // return "UNDEFINED";
    }
}
