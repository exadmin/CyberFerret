package com.github.exadmin.cyberferret.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiscUtilsTests {
    /**
     * Verifies that missing version metadata produces the safe fallback without side effects beyond warning logging.
     */
    @Test
    void returnsUnknownWhenVersionResourceIsMissing() {
        assertEquals("unknown", MiscUtils.loadApplicationVersion(null));
    }

    /**
     * Verifies that a valid properties stream returns its trimmed application version.
     */
    @Test
    void returnsVersionFromProperties() {
        assertEquals("2.1.2", loadVersion("application.version=2.1.2"));
    }

    /**
     * Verifies that a blank application version produces the safe fallback and a warning.
     */
    @Test
    void returnsUnknownWhenVersionPropertyIsBlank() {
        assertEquals("unknown", loadVersion("application.version=  "));
    }

    /**
     * Verifies that malformed properties produce the safe fallback and a warning instead of an exception.
     */
    @Test
    void returnsUnknownWhenVersionPropertiesAreMalformed() {
        assertEquals("unknown", loadVersion("application.version=\\uinvalid"));
    }

    /**
     * Verifies that an input failure produces the safe fallback and a warning instead of an exception.
     */
    @Test
    void returnsUnknownWhenVersionResourceCannotBeRead() {
        InputStream unreadable = new InputStream() {
            /**
             * Simulates a resource that fails on its first read without modifying external state.
             *
             * @return no value because this implementation always throws
             * @throws IOException on every invocation
             */
            @Override
            public int read() throws IOException {
                throw new IOException("Cannot read test stream");
            }
        };

        assertEquals("unknown", MiscUtils.loadApplicationVersion(unreadable));
    }

    /**
     * Loads version metadata from an in-memory ISO-8859-1 properties document without external side effects.
     *
     * @param properties properties document to load
     * @return the parsed application version or the production fallback
     */
    private static String loadVersion(String properties) {
        return MiscUtils.loadApplicationVersion(
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.ISO_8859_1)));
    }
}
