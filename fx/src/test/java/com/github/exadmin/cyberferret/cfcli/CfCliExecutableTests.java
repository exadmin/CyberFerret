package com.github.exadmin.cyberferret.cfcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CfCliExecutableTests {
    @TempDir
    Path tempDirectory;

    @Test
    void usesPathCommandWhenConfigurationIsBlank() {
        assertEquals("cfcli", new CfCliExecutable("  ").command());
        assertTrue(new CfCliExecutable("").validationError().isEmpty());
    }

    @Test
    void acceptsAnExplicitRegularFile() throws IOException {
        Path executable = Files.createFile(tempDirectory.resolve("cfcli.exe"));

        CfCliExecutable configured = new CfCliExecutable(executable.toString());

        assertEquals(executable.toString(), configured.command());
        assertTrue(configured.validationError().isEmpty());
    }

    @Test
    void rejectsMissingFilesAndDirectories() {
        assertTrue(new CfCliExecutable(tempDirectory.resolve("missing.exe").toString())
                .validationError().orElseThrow().contains("regular file"));
        assertTrue(new CfCliExecutable(tempDirectory.toString())
                .validationError().orElseThrow().contains("regular file"));
    }
}
