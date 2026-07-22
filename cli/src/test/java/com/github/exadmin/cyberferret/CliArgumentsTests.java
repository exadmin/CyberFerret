package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliArgumentsTests {
    @Test
    void parse_keepsDetailedScanWithOnePositionalArgument() {
        CliArguments arguments = CliArguments.parse(new String[]{"repository"});

        assertEquals(CliArguments.Command.DETAILED_SCAN, arguments.command());
        assertEquals(Path.of("repository").normalize(), arguments.repository());
        assertNull(arguments.stagedFilesList());
        assertNull(arguments.cacheDirectory());
        assertFalse(arguments.offline());
    }

    @Test
    void parse_keepsDetailedScanWithStagedFilesList() {
        CliArguments arguments = CliArguments.parse(new String[]{"repository", "staged.txt"});

        assertEquals(CliArguments.Command.DETAILED_SCAN, arguments.command());
        assertEquals(Path.of("staged.txt").normalize(), arguments.stagedFilesList());
    }

    @Test
    void parse_readsQuickModeOptions() {
        CliArguments arguments = CliArguments.parse(new String[]{
                "--mode=quick",
                "--offline",
                "--cache-dir=cache",
                "repository"
        });

        assertEquals(CliArguments.Command.QUICK_SCAN, arguments.command());
        assertEquals(Path.of("repository").normalize(), arguments.repository());
        assertEquals(Path.of("cache").normalize(), arguments.cacheDirectory());
        assertTrue(arguments.offline());
    }

    @Test
    void parse_readsDictionaryVersionCommand() {
        CliArguments arguments = CliArguments.parse(new String[]{
                "--dictionary-version",
                "--cache-dir=cache"
        });

        assertEquals(CliArguments.Command.DICTIONARY_VERSION, arguments.command());
        assertNull(arguments.repository());
        assertEquals(Path.of("cache").normalize(), arguments.cacheDirectory());
        assertFalse(arguments.offline());
    }

    @Test
    void parse_rejectsInvalidCombinations() {
        assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[]{}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--mode=quick"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--dictionary-version", "repository"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--dictionary-version", "--offline"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--unknown", "repository"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--mode=other", "repository"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--cache-dir=", "repository"}));
    }
}
