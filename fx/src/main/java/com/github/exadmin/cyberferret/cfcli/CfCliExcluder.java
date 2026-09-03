package com.github.exadmin.cyberferret.cfcli;

import com.github.exadmin.cyberferret.model.FoundPathItem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.github.exadmin.cyberferret.AppConstants.CFCLI_EXCLUSION_TIMEOUT_SECONDS;

public final class CfCliExcluder implements Runnable {
    @FunctionalInterface
    interface ProcessLauncher {
        Process start(List<String> command) throws IOException;
    }

    private final String executable;
    private final Path root;
    private final FoundPathItem item;
    private final boolean exclude;
    private final Consumer<String> successSink;
    private final Consumer<String> errorSink;
    private final Runnable completion;
    private final ProcessLauncher processLauncher;
    private final Duration timeout;

    /**
     * Creates a worker that updates an exclusion through the configured CLI executable.
     *
     * @param executable path or command used to start the CLI
     * @param root scan root passed to the CLI
     * @param item selected item to add or remove
     * @param exclude {@code true} to add the exclusion, or {@code false} to remove it
     * @param successSink receives successful CLI output
     * @param errorSink receives errors suitable for display
     * @param completion runs after the process finishes, fails, or times out
     */
    public CfCliExcluder(
            String executable,
            Path root,
            FoundPathItem item,
            boolean exclude,
            Consumer<String> successSink,
            Consumer<String> errorSink,
            Runnable completion) {
        this(executable, root, item, exclude, successSink, errorSink, completion,
                command -> new ProcessBuilder(command).redirectErrorStream(true).start(),
                Duration.ofSeconds(CFCLI_EXCLUSION_TIMEOUT_SECONDS));
    }

    /**
     * Creates a worker with an injectable process launcher and the production timeout.
     *
     * @param executable path or command used to start the CLI
     * @param root scan root passed to the CLI
     * @param item selected item to add or remove
     * @param exclude {@code true} to add the exclusion, or {@code false} to remove it
     * @param successSink receives successful CLI output
     * @param errorSink receives errors suitable for display
     * @param completion runs after the process finishes, fails, or times out
     * @param processLauncher starts the CLI process
     */
    CfCliExcluder(
            String executable,
            Path root,
            FoundPathItem item,
            boolean exclude,
            Consumer<String> successSink,
            Consumer<String> errorSink,
            Runnable completion,
            ProcessLauncher processLauncher) {
        this(executable, root, item, exclude, successSink, errorSink, completion, processLauncher,
                Duration.ofSeconds(CFCLI_EXCLUSION_TIMEOUT_SECONDS));
    }

    /**
     * Creates a worker with injectable process and timeout dependencies.
     *
     * @param executable path or command used to start the CLI
     * @param root scan root passed to the CLI
     * @param item selected item to add or remove
     * @param exclude {@code true} to add the exclusion, or {@code false} to remove it
     * @param successSink receives successful CLI output
     * @param errorSink receives errors suitable for display
     * @param completion runs after the process finishes, fails, or times out
     * @param processLauncher starts the CLI process
     * @param timeout maximum time allowed for the process and output collection
     */
    CfCliExcluder(
            String executable,
            Path root,
            FoundPathItem item,
            boolean exclude,
            Consumer<String> successSink,
            Consumer<String> errorSink,
            Runnable completion,
            ProcessLauncher processLauncher,
            Duration timeout) {
        this.executable = executable;
        this.root = root.toAbsolutePath().normalize();
        this.item = item;
        this.exclude = exclude;
        this.successSink = successSink;
        this.errorSink = errorSink;
        this.completion = completion;
        this.processLauncher = processLauncher;
        this.timeout = timeout;
    }

    /**
     * Runs the exclusion command, drains its output concurrently, and forcibly stops it when the timeout expires.
     */
    @Override
    public void run() {
        ExecutorService outputExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cyberferret-exclusion-output");
            thread.setDaemon(true);
            return thread;
        });
        Process process = null;
        Future<byte[]> outputFuture = null;
        try {
            process = processLauncher.start(command());
            Process runningProcess = process;
            outputFuture = outputExecutor.submit(() -> runningProcess.getInputStream().readAllBytes());

            long timeoutNanos = timeout.toNanos();
            long deadline = System.nanoTime() + timeoutNanos;
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TimeoutException();
            }
            long remainingNanos = Math.max(1, deadline - System.nanoTime());
            String output = new String(outputFuture.get(remainingNanos, TimeUnit.NANOSECONDS), StandardCharsets.UTF_8)
                    .strip();
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String details = output.isEmpty() ? "exit code " + exitCode : output;
                errorSink.accept("Cannot update exclusion: " + details);
                return;
            }
            successSink.accept(output);
        } catch (TimeoutException ex) {
            process.destroyForcibly();
            errorSink.accept("Exclusion update timed out after " + timeout.toSeconds() + " seconds.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            errorSink.accept("Exclusion update was interrupted.");
        } catch (Exception ex) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            errorSink.accept("Cannot update exclusion: " + rootMessage(ex));
        } finally {
            if (outputFuture != null) outputFuture.cancel(true);
            outputExecutor.shutdownNow();
            completion.run();
        }
    }

    List<String> command() throws IOException {
        return List.of(
                executable,
                "exclude",
                exclude ? "add" : "remove",
                root.toString(),
                encodeArgument(encodeTarget()));
    }

    private static String encodeArgument(String json) {
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "BASE64:" + encoded;
    }

    private String encodeTarget() throws IOException {
        String relativePath = relativePath();
        return switch (item.getType()) {
            case SIGNATURE -> {
                String found = item.getFoundString();
                if (found == null || found.isEmpty()) {
                    throw new IOException("Selected signature has no detected value.");
                }
                yield "{\"type\":\"found\",\"found\":" + quote(found) + ",\"file\":" + quote(relativePath) + "}";
            }
            case FILE -> "{\"type\":\"file\",\"file\":" + quote(relativePath) + "}";
            case DIRECTORY -> "{\"type\":\"folder\",\"folder\":" + quote(relativePath) + "}";
            default -> throw new IOException("Selected item type cannot be excluded: " + item.getType());
        };
    }

    private String relativePath() throws IOException {
        Path itemPath = item.getFilePath().toAbsolutePath().normalize();
        if (!itemPath.startsWith(root)) {
            throw new IOException("Selected item is outside the scan root: " + itemPath);
        }
        String relative = root.relativize(itemPath).toString().replace(File.separatorChar, '/');
        if (relative.isEmpty()) {
            throw new IOException("The scan root cannot be excluded.");
        }
        return relative;
    }

    private static String quote(String value) {
        StringBuilder json = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (current < 0x20) {
                        json.append(String.format("\\u%04x", (int) current));
                    } else {
                        json.append(current);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private static String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
