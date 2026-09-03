package com.github.exadmin.cyberferret.cfcli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfCliScannerTests {
    @TempDir
    Path root;

    @Test
    public void runsExpectedCommandAndStreamsStdoutAndStderr() {
        AtomicReference<List<String>> command = new AtomicReference<>();
        List<CfCliMessage> messages = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        AtomicInteger completions = new AtomicInteger();
        FakeProcess process = new FakeProcess(
                "TEXT: starting\nJSON: {\"type\":\"list\",\"file\":\"src/file.txt\"}\n",
                "diagnostic\n",
                0);
        CfCliScanner scanner = new CfCliScanner(
                root,
                messages::add,
                logs::add,
                errors::add,
                completions::incrementAndGet,
                requestedCommand -> {
                    command.set(List.copyOf(requestedCommand));
                    return process;
                });

        scanner.run();

        assertEquals(List.of("cfcli", "--mode=json", "--verbose=true", root.toAbsolutePath().normalize().toString()),
                command.get());
        assertEquals(1, messages.size());
        assertTrue(logs.contains("TEXT: starting"));
        assertTrue(logs.contains("diagnostic"));
        assertTrue(errors.isEmpty());
        assertEquals(1, completions.get());
    }

    @Test
    public void treatsExitCodeTwoAsSuccess() {
        List<String> errors = new ArrayList<>();
        AtomicInteger completions = new AtomicInteger();
        CfCliScanner scanner = scannerFor(new FakeProcess("", "", 2), errors, completions);

        scanner.run();

        assertTrue(errors.isEmpty());
        assertEquals(1, completions.get());
    }

    @Test
    public void reportsFailureExitAndMalformedProtocol() {
        List<String> exitErrors = new ArrayList<>();
        AtomicInteger exitCompletions = new AtomicInteger();
        scannerFor(new FakeProcess("", "failure\n", 3), exitErrors, exitCompletions).run();

        List<String> protocolErrors = new ArrayList<>();
        AtomicInteger protocolCompletions = new AtomicInteger();
        scannerFor(new FakeProcess("JSON: {invalid}\n", "", 0), protocolErrors, protocolCompletions).run();

        assertTrue(exitErrors.getFirst().contains("exit code 3"));
        assertTrue(protocolErrors.getFirst().contains("Cannot process cfcli output"));
        assertEquals(1, exitCompletions.get());
        assertEquals(1, protocolCompletions.get());
    }

    @Test
    public void completesOnlyAfterInterruptedStreamCallbacksFinish() throws InterruptedException {
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch completionCalled = new CountDownLatch(1);
        AtomicBoolean callbackFinished = new AtomicBoolean();
        List<String> errors = new ArrayList<>();
        CfCliScanner scanner = new CfCliScanner(
                root,
                ignored -> {
                },
                ignored -> {
                    callbackStarted.countDown();
                    while (true) {
                        try {
                            releaseCallback.await();
                            break;
                        } catch (InterruptedException ex) {
                            // Keep the callback active until the test releases it.
                        }
                    }
                    callbackFinished.set(true);
                },
                errors::add,
                completionCalled::countDown,
                ignored -> new FakeProcess("TEXT: wait\n", "", 0));
        Thread scannerThread = new Thread(scanner);

        scannerThread.start();
        assertTrue(callbackStarted.await(1, TimeUnit.SECONDS));
        scannerThread.interrupt();

        boolean completedBeforeCallback;
        try {
            completedBeforeCallback = completionCalled.await(200, TimeUnit.MILLISECONDS);
        } finally {
            releaseCallback.countDown();
        }
        scannerThread.join(1_000);

        assertFalse(completedBeforeCallback);
        assertFalse(scannerThread.isAlive());
        assertTrue(scannerThread.isInterrupted());
        assertTrue(callbackFinished.get());
        assertEquals(0, completionCalled.getCount());
        assertTrue(errors.getFirst().contains("interrupted"));
    }

    private CfCliScanner scannerFor(
            Process process,
            List<String> errors,
            AtomicInteger completions) {
        return new CfCliScanner(
                root,
                ignored -> {
                },
                ignored -> {
                },
                errors::add,
                completions::incrementAndGet,
                ignored -> process);
    }

    private static final class FakeProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        private final int exitCode;
        private boolean destroyed;

        private FakeProcess(String stdout, String stderr, int exitCode) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
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
            destroyed = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyed;
        }
    }
}
