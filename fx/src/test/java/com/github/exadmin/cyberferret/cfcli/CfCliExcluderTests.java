package com.github.exadmin.cyberferret.cfcli;

import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfCliExcluderTests {
    @TempDir
    Path root;

    @Test
    void buildsCommandsForEveryItemType() throws Exception {
        FoundPathItem signature = item("src/file.txt", ItemType.SIGNATURE);
        signature.setFoundString("secret \"value\"\n");

        List<String> signatureCommand = excluder(signature, true).command();
        assertEquals("custom-cfcli", signatureCommand.getFirst());
        assertEquals("exclude", signatureCommand.get(1));
        assertEquals("add", signatureCommand.get(2));
        assertEquals(root.toAbsolutePath().normalize().toString(), signatureCommand.get(3));
        assertEquals(
                "{\"type\":\"found\",\"found\":\"secret \\\"value\\\"\\n\",\"file\":\"src/file.txt\"}",
                decodeTarget(signatureCommand.getLast()));
        assertEquals(
                "{\"type\":\"file\",\"file\":\"src/file.txt\"}",
                decodeTarget(excluder(item("src/file.txt", ItemType.FILE), true).command().getLast()));
        assertEquals(
                "{\"type\":\"folder\",\"folder\":\"src/generated\"}",
                decodeTarget(excluder(item("src/generated", ItemType.DIRECTORY), false).command().getLast()));
        assertEquals("remove", excluder(item("src/generated", ItemType.DIRECTORY), false).command().get(2));
    }

    @Test
    void reportsSuccessAndCompletes() {
        AtomicReference<List<String>> command = new AtomicReference<>();
        List<String> successes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        AtomicInteger completions = new AtomicInteger();
        CfCliExcluder excluder = new CfCliExcluder(
                "cfcli",
                root,
                item("src/file.txt", ItemType.FILE),
                true,
                successes::add,
                errors::add,
                completions::incrementAndGet,
                requestedCommand -> {
                    command.set(List.copyOf(requestedCommand));
                    return new FakeProcess("TEXT: Exclusion added: report.json\n", 0);
                });

        excluder.run();

        assertEquals("add", command.get().get(2));
        assertEquals(List.of("TEXT: Exclusion added: report.json"), successes);
        assertTrue(errors.isEmpty());
        assertEquals(1, completions.get());
    }

    @Test
    void reportsNativeFailureWithoutSuccess() {
        List<String> successes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        AtomicInteger completions = new AtomicInteger();
        CfCliExcluder excluder = new CfCliExcluder(
                "cfcli",
                root,
                item("src/file.txt", ItemType.FILE),
                false,
                successes::add,
                errors::add,
                completions::incrementAndGet,
                ignored -> new FakeProcess("TEXT: Cannot update report. No files were changed.\n", 1));

        excluder.run();

        assertTrue(successes.isEmpty());
        assertTrue(errors.getFirst().contains("No files were changed"));
        assertEquals(1, completions.get());
    }

    private CfCliExcluder excluder(FoundPathItem item, boolean exclude) {
        return new CfCliExcluder(
                "custom-cfcli",
                root,
                item,
                exclude,
                ignored -> {
                },
                ignored -> {
                },
                () -> {
                });
    }

    private FoundPathItem item(String relativePath, ItemType type) {
        return new FoundPathItem(root.resolve(relativePath), type, null);
    }

    private static String decodeTarget(String argument) {
        assertTrue(argument.startsWith("BASE64:"));
        byte[] decoded = Base64.getUrlDecoder().decode(argument.substring("BASE64:".length()));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static final class FakeProcess extends Process {
        private final InputStream output;
        private final int exitCode;

        private FakeProcess(String output, int exitCode) {
            this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return output;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }
    }
}
