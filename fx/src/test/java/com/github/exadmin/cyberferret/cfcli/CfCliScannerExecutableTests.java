package com.github.exadmin.cyberferret.cfcli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CfCliScannerExecutableTests {
    @TempDir
    Path root;

    @Test
    void usesConfiguredExecutableAsFirstCommandElement() {
        AtomicReference<List<String>> command = new AtomicReference<>();
        CfCliScanner scanner = new CfCliScanner(
                "C:\\Tools\\cfcli.exe",
                root,
                ignored -> {},
                ignored -> {},
                ignored -> {},
                () -> {},
                requestedCommand -> {
                    command.set(List.copyOf(requestedCommand));
                    return new CompletedProcess();
                });

        scanner.run();

        assertEquals("C:\\Tools\\cfcli.exe", command.get().getFirst());
    }

    private static final class CompletedProcess extends Process {
        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }
    }
}
