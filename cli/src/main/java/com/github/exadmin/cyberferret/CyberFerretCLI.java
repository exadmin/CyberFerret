package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.async.RunnableScanner;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.utils.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This is CLI version of CyberFerret app with focus on quick initialization and run triggered by pre-commit framework.
 */
public class CyberFerretCLI {
    public static void main(String[] args) {
        int exitCode = run(args, System.getenv(), System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        CliArguments arguments;
        try {
            arguments = CliArguments.parse(args);
        } catch (RuntimeException exception) {
            err.println("Invalid command-line arguments.");
            printUsage(err);
            return usesAutomationOptions(args) ? 2 : 1;
        }

        String password = environment.get(AppConstants.SYS_ENV_VAR_PASSWORD);
        boolean detailed = arguments.command() == CliArguments.Command.DETAILED_SCAN;
        if (password == null || password.isBlank()) {
            err.println("Dictionary password is unavailable.");
            return detailed ? 1 : 2;
        }

        try {
            DictionarySession dictionary = DictionarySession.prepare(
                    arguments.cacheDirectory(),
                    arguments.offline(),
                    password);
            if (arguments.command() == CliArguments.Command.DICTIONARY_VERSION) {
                out.println(dictionary.dictionaryVersion());
                return 0;
            }
            return runScan(arguments, dictionary, out, err);
        } catch (DictionarySession.DictionaryException exception) {
            err.println(exception.getMessage());
            return detailed ? 1 : 2;
        } catch (Exception exception) {
            err.println("CyberFerret could not complete the requested operation.");
            return detailed ? 1 : 2;
        }
    }

    private static int runScan(
            CliArguments arguments,
            DictionarySession dictionary,
            PrintStream out,
            PrintStream err) throws IOException {
        Path repository = arguments.repository().toAbsolutePath().normalize();
        boolean detailed = arguments.command() == CliArguments.Command.DETAILED_SCAN;
        if (!FileUtils.isPathToDir(repository)) {
            err.println("The repository path is not a readable directory.");
            return detailed ? 1 : 2;
        }

        List<Path> files;
        if (arguments.stagedFilesList() != null) {
            Path listPath = arguments.stagedFilesList().toAbsolutePath().normalize();
            if (!Files.isRegularFile(listPath)) {
                err.println("The staged-files list is unavailable.");
                return 1;
            }
            files = loadStagedFiles(repository, listPath);
        } else {
            files = loadFilesFromRepository(repository);
        }
        if (files.isEmpty()) return 0;

        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setSilent(!detailed);
        runnableScanner.setPrintToConsole(detailed);
        runnableScanner.setRevealFindings(detailed);
        runnableScanner.setFoundItemsContainer(new FoundItemsContainer());
        runnableScanner.setSignaturesMap(dictionary.signaturesMap());
        runnableScanner.setAllowedSignaturesMap(dictionary.allowedSignaturesMap());
        runnableScanner.setExcludeExtMap(dictionary.excludeExtsMap());
        runnableScanner.setDirToScan(repository.toString());
        runnableScanner.setStagedFiles(files);
        runnableScanner.run();

        if (runnableScanner.hasOperationalFailure()) {
            err.println("CyberFerret could not complete the repository scan.");
            return detailed ? 1 : 2;
        }
        if (runnableScanner.isAnySignatureFound()) {
            if (!detailed) out.println("Findings detected");
            return 1;
        }
        return 0;
    }

    private static boolean usesAutomationOptions(String[] args) {
        for (String argument : args) {
            if (argument.startsWith("--")) return true;
        }
        return false;
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Usage:");
        stream.println("  CyberFerretCLI REPOSITORY [STAGED_FILES_LIST]");
        stream.println("  CyberFerretCLI --mode=quick [--offline] [--cache-dir=PATH] REPOSITORY");
        stream.println("  CyberFerretCLI --dictionary-version [--cache-dir=PATH]");
    }

    static List<Path> loadStagedFiles(Path rootPathToScan, Path stagedFilesListPath) throws IOException {
        List<Path> stagedFiles = new ArrayList<>();
        List<String> lines = Files.readAllLines(stagedFilesListPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null) continue;
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = value.substring(1, value.length() - 1).trim();
            }
            if (value.isEmpty()) continue;
            Path path = Paths.get(value);
            if (!path.isAbsolute()) {
                path = rootPathToScan.resolve(path);
            }
            path = path.normalize();
            stagedFiles.add(path);
            ConsoleUtils.trace("Staged file = " + path);
        }
        return stagedFiles;
    }

    static List<Path> loadFilesFromRepository(Path rootPathToScan) throws IOException {
        return new RepositoryFileLoader().load(rootPathToScan);
    }
}
