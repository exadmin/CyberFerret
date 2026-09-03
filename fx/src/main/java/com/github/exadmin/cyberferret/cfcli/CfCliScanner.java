package com.github.exadmin.cyberferret.cfcli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class CfCliScanner implements Runnable {
    @FunctionalInterface
    public interface MessageHandler {
        void handle(CfCliMessage message) throws Exception;
    }

    @FunctionalInterface
    interface ProcessLauncher {
        Process start(List<String> command) throws IOException;
    }

    private final String executable;
    private final Path root;
    private final MessageHandler messageHandler;
    private final Consumer<String> logSink;
    private final Consumer<String> errorSink;
    private final Runnable completion;
    private final ProcessLauncher processLauncher;
    private final CfCliMessageParser parser = new CfCliMessageParser();

    public CfCliScanner(
            Path root,
            MessageHandler messageHandler,
            Consumer<String> logSink,
            Consumer<String> errorSink,
            Runnable completion) {
        this("cfcli", root, messageHandler, logSink, errorSink, completion,
                command -> new ProcessBuilder(command).start());
    }

    public CfCliScanner(
            String executable,
            Path root,
            MessageHandler messageHandler,
            Consumer<String> logSink,
            Consumer<String> errorSink,
            Runnable completion) {
        this(executable, root, messageHandler, logSink, errorSink, completion,
                command -> new ProcessBuilder(command).start());
    }

    CfCliScanner(
            Path root,
            MessageHandler messageHandler,
            Consumer<String> logSink,
            Consumer<String> errorSink,
            Runnable completion,
            ProcessLauncher processLauncher) {
        this("cfcli", root, messageHandler, logSink, errorSink, completion, processLauncher);
    }

    CfCliScanner(
            String executable,
            Path root,
            MessageHandler messageHandler,
            Consumer<String> logSink,
            Consumer<String> errorSink,
            Runnable completion,
            ProcessLauncher processLauncher) {
        this.executable = executable;
        this.root = root.toAbsolutePath().normalize();
        this.messageHandler = messageHandler;
        this.logSink = logSink;
        this.errorSink = errorSink;
        this.completion = completion;
        this.processLauncher = processLauncher;
    }

    /**
     * Runs the scanner process and invokes completion only after both process streams stop producing callbacks.
     */
    @Override
    public void run() {
        ExecutorService streamExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "cyberferret-go-cli-stream");
            thread.setDaemon(true);
            return thread;
        });
        Process process = null;
        boolean streamsAwaited = false;
        boolean restoreInterrupt = false;
        try {
            List<String> command = List.of(
                    executable,
                    "--mode=json",
                    "--verbose=true",
                    root.toString());
            process = processLauncher.start(command);
            Process runningProcess = process;
            Future<?> stdout = streamExecutor.submit(() -> pumpStdout(runningProcess));
            Future<?> stderr = streamExecutor.submit(() -> pumpLogs(runningProcess.getErrorStream()));

            int exitCode = process.waitFor();
            await(stdout);
            await(stderr);
            streamsAwaited = true;
            if (exitCode != 0 && exitCode != 2) {
                throw new IOException("cfcli finished with exit code " + exitCode);
            }
        } catch (InterruptedException ex) {
            restoreInterrupt = true;
            errorSink.accept("cfcli scanning was interrupted");
        } catch (Exception ex) {
            errorSink.accept("Cannot process cfcli output: " + rootMessage(ex));
        } finally {
            restoreInterrupt |= finishStreamTasks(process, streamExecutor, streamsAwaited);
            try {
                completion.run();
            } finally {
                if (restoreInterrupt) Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stops unfinished process I/O and waits until all stream tasks terminate.
     *
     * @param process scanner process, or {@code null} when startup failed
     * @param streamExecutor executor that owns both stream-pump tasks
     * @param streamsAwaited whether both pump futures completed before cleanup
     * @return {@code true} when cleanup was interrupted and the caller must restore the interrupt flag
     */
    private static boolean finishStreamTasks(
            Process process, ExecutorService streamExecutor, boolean streamsAwaited) {
        boolean interrupted = false;
        if (!streamsAwaited && process != null) {
            if (process.isAlive()) process.destroyForcibly();
            while (process.isAlive()) {
                try {
                    process.waitFor();
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
            closeProcessStreams(process);
        }

        streamExecutor.shutdownNow();
        while (!streamExecutor.isTerminated()) {
            try {
                streamExecutor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    /**
     * Closes every process stream to release pump tasks blocked in an I/O operation.
     *
     * @param process process whose streams must be closed
     */
    private static void closeProcessStreams(Process process) {
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
        closeQuietly(process.getOutputStream());
    }

    /**
     * Closes a process stream without masking the scanner's original failure.
     *
     * @param stream stream to close
     */
    private static void closeQuietly(AutoCloseable stream) {
        try {
            stream.close();
        } catch (Exception ignored) {
            // The original scanner result remains the primary outcome.
        }
    }

    private void pumpStdout(Process process) {
        try (BufferedReader reader = utf8Reader(process.getInputStream())) {
            String line;
            while ((line = reader.readLine()) != null) {
                Optional<CfCliMessage> message = parser.parse(line);
                if (message.isPresent()) {
                    messageHandler.handle(message.get());
                } else {
                    logSink.accept(line);
                }
            }
        } catch (Exception ex) {
            process.destroyForcibly();
            throw new StreamPumpException(ex);
        }
    }

    private void pumpLogs(InputStream stream) {
        try (BufferedReader reader = utf8Reader(stream)) {
            String line;
            while ((line = reader.readLine()) != null) logSink.accept(line);
        } catch (IOException ex) {
            throw new StreamPumpException(ex);
        }
    }

    private static BufferedReader utf8Reader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static void await(Future<?> future) throws Exception {
        try {
            future.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof StreamPumpException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw new IOException("Unexpected stream processing failure", cause);
        }
    }

    private static String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class StreamPumpException extends RuntimeException {
        private StreamPumpException(Throwable cause) {
            super(cause);
        }
    }
}
