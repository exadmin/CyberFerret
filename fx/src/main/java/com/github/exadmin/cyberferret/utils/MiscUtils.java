package com.github.exadmin.cyberferret.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MiscUtils {
    private static final String UNKNOWN_VERSION = "unknown";
    private static final Logger LOG = LoggerFactory.getLogger(MiscUtils.class);

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    private static final String VERSION_PROPERTIES_RESOURCE = "/version.properties";

    /**
     * Loads the application version from the classpath metadata.
     *
     * <p>This method logs a warning and returns {@code unknown} when the resource is missing, malformed, unreadable,
     * or does not define a nonblank version. It opens and closes the classpath resource.</p>
     *
     * @return the application version, or {@code unknown} when the metadata is unavailable or invalid
     */
    public static String loadApplicationVersion() {
        try (InputStream input = MiscUtils.class.getResourceAsStream(VERSION_PROPERTIES_RESOURCE)) {
            return loadApplicationVersion(input);
        } catch (IOException ex) {
            LOG.warn("Cannot close application version resource {}", VERSION_PROPERTIES_RESOURCE, ex);
            return UNKNOWN_VERSION;
        }
    }

    /**
     * Loads the application version from a properties stream.
     *
     * <p>This method logs a warning and returns {@code unknown} when the stream is missing, malformed, unreadable,
     * or does not define a nonblank version. It does not close the supplied stream.</p>
     *
     * @param input stream containing the application version, or {@code null} when the resource is unavailable
     * @return the application version, or {@code unknown} when it cannot be loaded
     */
    static String loadApplicationVersion(InputStream input) {
        if (input == null) {
            LOG.warn("Application version resource {} is unavailable", VERSION_PROPERTIES_RESOURCE);
            return UNKNOWN_VERSION;
        }

        Properties properties = new Properties();
        try {
            properties.load(input);
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warn("Cannot load application version from {}", VERSION_PROPERTIES_RESOURCE, ex);
            return UNKNOWN_VERSION;
        }

        String version = properties.getProperty("application.version");
        if (version == null || version.isBlank()) {
            LOG.warn("Application version resource {} does not define application.version", VERSION_PROPERTIES_RESOURCE);
            return UNKNOWN_VERSION;
        }
        return version.trim();
    }
}
