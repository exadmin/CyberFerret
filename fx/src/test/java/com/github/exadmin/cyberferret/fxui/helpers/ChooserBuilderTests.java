package com.github.exadmin.cyberferret.fxui.helpers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChooserBuilderTests {
    @TempDir
    Path tempDirectory;

    @Test
    void resolvesExistingFileParentAsInitialDirectory() throws IOException {
        Path executable = Files.createFile(tempDirectory.resolve("cfcli.exe"));

        assertEquals(tempDirectory.toFile(), ChooserBuilder.initialDirectory(executable.toString()));
    }

    @Test
    void leavesInitialDirectoryUnsetForBlankOrMissingPaths() {
        assertNull(ChooserBuilder.initialDirectory(""));
        assertNull(ChooserBuilder.initialDirectory(tempDirectory.resolve("missing.exe").toString()));
    }
}
