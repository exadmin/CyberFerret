package com.github.exadmin.cyberferret.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class for discovering files in a git repository while respecting .gitignore rules.
 * Uses git CLI commands to get the list of tracked and untracked (non-ignored) files.
 */
public class GitFileDiscovery {
    private static final Logger log = LoggerFactory.getLogger(GitFileDiscovery.class);

    /**
     * Discovers all files that should be scanned in a git repository.
     * This includes:
     * - All tracked files (files committed to git)
     * - All untracked files that are NOT ignored by .gitignore
     *
     * @param repositoryRoot the root directory to scan
     * @return Optional containing a Set of file Paths to scan, or empty if git is unavailable
     */
    public static Optional<Set<Path>> discoverGitFiles(Path repositoryRoot) {
        try {
            // Get tracked files
            Set<Path> trackedFiles = executeGitLsFiles(repositoryRoot, false);
            Set<Path> allFiles = new HashSet<>(trackedFiles);
            log.info("Found {} tracked files", trackedFiles.size());

            // Get untracked but not ignored files
            Set<Path> untrackedFiles = executeGitLsFiles(repositoryRoot, true);
            allFiles.addAll(untrackedFiles);
            log.info("Found {} untracked (non-ignored) files", untrackedFiles.size());

            log.info("Total files to scan from git: {}", allFiles.size());
            return Optional.of(allFiles);

        } catch (Exception e) {
            log.warn("Error discovering files via git, will fall back to regular file scan: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gets the root directory of the git repository.
     *
     * @param directory a directory inside the git repository
     * @return the git repository root path, or null if unable to determine
     */
    private static Path getGitRepositoryRoot(Path directory) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--show-toplevel");
            pb.directory(directory.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();

            if (exitCode == 0 && output != null && !output.trim().isEmpty()) {
                return Paths.get(output.trim());
            }
        } catch (Exception e) {
            log.debug("Error getting git repository root: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Executes git ls-files command to get list of files.
     *
     * @param gitRoot       the git repository root directory
     * @param untrackedOnly if true, gets untracked non-ignored files; if false, gets tracked files
     * @return Set of file paths relative to gitRoot
     */
    private static Set<Path> executeGitLsFiles(Path gitRoot, boolean untrackedOnly) throws IOException, InterruptedException {
        Set<Path> files = new HashSet<>();

        ProcessBuilder pb;
        if (untrackedOnly) {
            // Get untracked files that are NOT ignored
            pb = new ProcessBuilder("git", "ls-files", "--others", "--exclude-standard");
        } else {
            // Get tracked files
            pb = new ProcessBuilder("git", "ls-files");
        }

        pb.directory(gitRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(process.getInputStream())))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    // Convert relative path from git to absolute path
                    Path filePath = gitRoot.resolve(line).normalize();
                    // Only include if it's a regular file (not a directory)
                    if (Files.isRegularFile(filePath)) {
                        files.add(filePath);
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("Git command exited with code {}", exitCode);
        }

        return files;
    }

    /**
     * Reads all output from a process.
     *
     * @param process the process to read from
     * @return the complete output as a string
     */
    private static String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(process.getInputStream())))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}
