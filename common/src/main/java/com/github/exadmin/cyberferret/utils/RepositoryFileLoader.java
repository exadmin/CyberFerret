package com.github.exadmin.cyberferret.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        byte[] trackedOutput = executeGitLsFiles(root, "--cached", "--stage", "-z");
        byte[] untrackedOutput = executeGitLsFiles(root, "--others", "--exclude-standard", "-z");
        return loadSelectedPaths(root, parseGitSelection(trackedOutput, untrackedOutput));
    }

    private byte[] executeGitLsFiles(Path root, String... arguments) throws IOException {
        Process process;
        try {
            List<String> command = new ArrayList<>(gitCommand);
            command.addAll(List.of("-C", root.toString(), "ls-files"));
            command.addAll(List.of(arguments));
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

        return output;
    }

    private GitSelection parseGitSelection(byte[] trackedOutput, byte[] untrackedOutput) {
        Set<GitPathKey> files = new HashSet<>();
        Set<GitPathKey> directories = new HashSet<>();
        Set<GitPathKey> submodules = new HashSet<>();
        addGitEntries(trackedOutput, true, files, directories, submodules);
        addGitEntries(untrackedOutput, false, files, directories, submodules);
        return new GitSelection(files, directories, submodules);
    }

    private void addGitEntries(
            byte[] output,
            boolean hasStageMetadata,
            Set<GitPathKey> files,
            Set<GitPathKey> directories,
            Set<GitPathKey> submodules) {
        int entryStart = 0;
        for (int index = 0; index < output.length; index++) {
            if (output[index] != 0) {
                continue;
            }
            addGitEntry(
                    output,
                    entryStart,
                    index,
                    hasStageMetadata,
                    files,
                    directories,
                    submodules);
            entryStart = index + 1;
        }
    }

    private byte[] readProcessOutput(Future<byte[]> outputFuture) throws IOException, InterruptedException {
        try {
            return outputFuture.get();
        } catch (ExecutionException ex) {
            throw new IOException("Cannot read Git process output", ex.getCause());
        }
    }

    private void addGitEntry(
            byte[] output,
            int entryStart,
            int entryEnd,
            boolean hasStageMetadata,
            Set<GitPathKey> files,
            Set<GitPathKey> directories,
            Set<GitPathKey> submodules) {
        if (entryStart == entryEnd) {
            return;
        }
        int pathStart = entryStart;
        boolean isSubmodule = false;
        if (hasStageMetadata) {
            for (int index = entryStart; index < entryEnd; index++) {
                if (output[index] != '\t') {
                    continue;
                }
                isSubmodule = hasGitMode(output, entryStart, index, "160000");
                pathStart = index + 1;
                break;
            }
        }

        GitPathKey path = GitPathKey.fromGitOutput(output, pathStart, entryEnd);
        if (isSubmodule) {
            submodules.add(path);
        } else {
            files.add(path);
        }
        for (GitPathKey parent = path.parent(); parent != null; parent = parent.parent()) {
            directories.add(parent);
        }
    }

    private boolean hasGitMode(
            byte[] output,
            int entryStart,
            int metadataEnd,
            String expectedMode) {
        byte[] mode = expectedMode.getBytes(StandardCharsets.US_ASCII);
        if (metadataEnd - entryStart < mode.length) {
            return false;
        }
        for (int index = 0; index < mode.length; index++) {
            if (output[entryStart + index] != mode[index]) {
                return false;
            }
        }
        return true;
    }

    private List<Path> loadSelectedPaths(Path root, GitSelection selection) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (directory.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                if (directory.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                GitPathKey path = GitPathKey.fromNativePath(root, directory);
                if (selection.submodules().contains(path)) {
                    if (isGitRepository(directory)) {
                        files.addAll(loadFromGit(directory));
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!selection.directories().contains(path)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                GitPathKey path = GitPathKey.fromNativePath(root, file);
                boolean isScannableFile = attributes.isRegularFile()
                        || (attributes.isSymbolicLink() && Files.isRegularFile(file));
                if (selection.files().contains(path) && isScannableFile) {
                    files.add(file.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
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

    private record GitSelection(
            Set<GitPathKey> files,
            Set<GitPathKey> directories,
            Set<GitPathKey> submodules) {
    }
}
