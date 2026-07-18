package com.github.exadmin.cyberferret.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RepositoryFileLoader {
    private final List<String> gitCommand;

    public RepositoryFileLoader() {
        this(List.of("git"));
    }

    RepositoryFileLoader(List<String> gitCommand) {
        this.gitCommand = List.copyOf(gitCommand);
    }

    public List<Path> load(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (isGitRepository(normalizedRoot)) {
            return loadFromGit(normalizedRoot);
        }
        return loadFromFileSystem(normalizedRoot);
    }

    public boolean isGitRepository(Path root) {
        Path gitEntry = root.toAbsolutePath().normalize().resolve(".git");
        return Files.isRegularFile(gitEntry)
                || Files.isRegularFile(gitEntry.resolve("config"));
    }

    private List<Path> loadFromGit(Path root) throws IOException {
        Process process;
        try {
            List<String> command = new ArrayList<>(gitCommand);
            command.addAll(List.of(
                    "-C",
                    root.toString(),
                    "ls-files",
                    "--cached",
                    "--others",
                    "--exclude-standard",
                    "-z"));
            process = new ProcessBuilder(command).start();
        } catch (IOException ex) {
            throw new IOException("Cannot start Git to list repository files", ex);
        }

        int exitCode;
        byte[] output;
        byte[] errorOutput;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> outputFuture = executor.submit(() -> process.getInputStream().readAllBytes());
            Future<byte[]> errorFuture = executor.submit(() -> process.getErrorStream().readAllBytes());
            try {
                exitCode = process.waitFor();
                output = readProcessOutput(outputFuture);
                errorOutput = readProcessOutput(errorFuture);
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while listing Git repository files", ex);
            }
        }

        if (exitCode != 0) {
            String message = new String(errorOutput, StandardCharsets.UTF_8).trim();
            throw new IOException("Cannot list Git repository files: " + message);
        }

        List<Path> files = new ArrayList<>();
        int entryStart = 0;
        for (int index = 0; index < output.length; index++) {
            if (output[index] != 0) {
                continue;
            }
            addGitPath(root, output, entryStart, index, files);
            entryStart = index + 1;
        }
        return files;
    }

    private byte[] readProcessOutput(Future<byte[]> outputFuture) throws IOException, InterruptedException {
        try {
            return outputFuture.get();
        } catch (ExecutionException ex) {
            throw new IOException("Cannot read Git process output", ex.getCause());
        }
    }

    private void addGitPath(
            Path root,
            byte[] output,
            int entryStart,
            int entryEnd,
            List<Path> files) throws IOException {
        if (entryStart == entryEnd) {
            return;
        }
        String relativePath = new String(
                output,
                entryStart,
                entryEnd - entryStart,
                StandardCharsets.UTF_8);
        Path file = root.resolve(relativePath).normalize();
        if (file.startsWith(root) && Files.isRegularFile(file)) {
            files.add(file);
        } else if (file.startsWith(root)
                && Files.isDirectory(file)
                && isGitRepository(file)) {
            files.addAll(loadFromGit(file));
        }
    }

    private List<Path> loadFromFileSystem(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (directory.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    files.add(file.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
}
