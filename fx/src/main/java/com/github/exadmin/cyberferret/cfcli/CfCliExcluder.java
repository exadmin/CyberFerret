package com.github.exadmin.cyberferret.cfcli;

import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

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

    public CfCliExcluder(
            String executable,
            Path root,
            FoundPathItem item,
            boolean exclude,
            Consumer<String> successSink,
            Consumer<String> errorSink,
            Runnable completion) {
        this(executable, root, item, exclude, successSink, errorSink, completion,
                command -> new ProcessBuilder(command).redirectErrorStream(true).start());
    }

    CfCliExcluder(
            String executable,
            Path root,
            FoundPathItem item,
            boolean exclude,
            Consumer<String> successSink,
            Consumer<String> errorSink,
            Runnable completion,
            ProcessLauncher processLauncher) {
        this.executable = executable;
        this.root = root.toAbsolutePath().normalize();
        this.item = item;
        this.exclude = exclude;
        this.successSink = successSink;
        this.errorSink = errorSink;
        this.completion = completion;
        this.processLauncher = processLauncher;
    }

    @Override
    public void run() {
        Process process = null;
        try {
            process = processLauncher.start(command());
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String details = output.isEmpty() ? "exit code " + exitCode : output;
                errorSink.accept("Cannot update exclusion: " + details);
                return;
            }
            successSink.accept(output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            errorSink.accept("Exclusion update was interrupted.");
        } catch (Exception ex) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            errorSink.accept("Cannot update exclusion: " + rootMessage(ex));
        } finally {
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
