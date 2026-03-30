package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.async.RunnableCheckOnlineDictionary;
import com.github.exadmin.cyberferret.async.RunnableScanner;
import com.github.exadmin.cyberferret.async.RunnableSigsLoader;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.utils.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * This is CLI version of CyberFerret app with focus on quick initialization and run triggered by pre-commit framework.
 */
public class CyberFerretCLI {
    private static void printUsage() {
        String errMsg = """
                Usage: CyberFerretCLI $PATH_TO_REPOSITORY_TO_SCAN $PATH_TO_FILE_WITH_LIST_OF_FILES"
                Also, note that '{}' System Environment variable must be set
                """;
        errMsg = ConsoleUtils.format(errMsg, AppConstants.SYS_ENV_VAR_PASSWORD);
        System.out.println(errMsg);
    }

    private static void terminateAppWithErrorCode(boolean printUsage) {
        if (printUsage) printUsage();
        System.exit(1);
    }

    public static void main(String[] args) {
        try {
            _main(args);
        } catch (Throwable t) {
            System.out.println("Error: " + t.getMessage());
            System.exit(1);
        }
    }

    private static void _main(String[] args) {
        // Overall logic
        // Step1: Check required program arguments are set
        // Step2: Check required system env variables are set
        // Step3: Ensure actual dictionary is downloaded
        // Step4: Download dictionary if required
        // Step5: Decrypt dictionary
        // Step6: Run check over the git-repository

        String appVer = MiscUtils.loadApplicationVersion();
        ConsoleUtils.info("CyberFerretCLI version: " + appVer);

        // Step1: Check required program arguments are set
        if (args.length != 2) {
            ConsoleUtils.error("Unexpected number of command line arguments");
            terminateAppWithErrorCode(true);
        }

        final Path repoPathToScan = Path.of(args[0]);
        if (!FileUtils.isPathToDir(repoPathToScan)) {
            ConsoleUtils.error("Invalid path to scan directory {}", repoPathToScan);
            terminateAppWithErrorCode(true);
        }

        // Step2: Check required system env variable is set
        final String pass = System.getenv(AppConstants.SYS_ENV_VAR_PASSWORD);
        if (pass == null || pass.isEmpty()) {
            ConsoleUtils.error("Environment variable '{}' must be set", AppConstants.SYS_ENV_VAR_PASSWORD);
            terminateAppWithErrorCode(true);
        }

        // Check that file with staged files is set
        Path stagedFilesListPath = Path.of(args[1]);
        if (!Files.isRegularFile(stagedFilesListPath)) {
            ConsoleUtils.error("Invalid path to files list {}", stagedFilesListPath);
            terminateAppWithErrorCode(true);
        }

        List<Path> stagedFiles = new ArrayList<>();
        boolean isErrorFound = false;
        try {
            stagedFiles = loadStagedFiles(repoPathToScan, stagedFilesListPath);

            if (stagedFiles.isEmpty()) {
                ConsoleUtils.error("No staged files found in the file {}", stagedFilesListPath);
                isErrorFound = true;
            }
        } catch (IOException ex) {
            ConsoleUtils.error("Error while reading staged files list. " + ex.getMessage());
            isErrorFound = true;
        } finally {
            if (isErrorFound) {
                terminateAppWithErrorCode(false);
            }
        }

        // Step3: Ensure actual dictionary is downloaded
        // The dictionarry will be downloaded into Git Global Hook path (only once per 4 hours)
        RunnableCheckOnlineDictionary dictionaryDownloader = new RunnableCheckOnlineDictionary(true);
        dictionaryDownloader.setPrintToConsole(true);
        dictionaryDownloader.run();

        // Step5: Run checks
        RunnableSigsLoader sigsLoader = new RunnableSigsLoader(true);
        sigsLoader.setPrintToConsole(true);
        try {
            String prefix = GitUtils.getGlobalConfigValue("core.hooksPath");
            if (MiscUtils.isEmpty(prefix)) {
                ConsoleUtils.error("Global hooksPath is not empty");
                terminateAppWithErrorCode(false);
            }
            Path path = Paths.get(prefix, AppConstants.DICTIONARY_FILE_PATH_ENCRYPTED);
            String encryptedBody = FileUtils.readFile(path);
            String decryptedBody = PasswordBasedEncryption.decrypt(encryptedBody, pass);

            byte[] bytes = decryptedBody.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(bytes);
            sigsLoader.setInputStream(inputStream);
            sigsLoader.run();
        } catch (Exception ex) {
            ConsoleUtils.error("Error while loading signatures. " + ex.getMessage());
            terminateAppWithErrorCode(false);
        }

        // Step6: Run scanner
        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();

        RunnableScanner runnableScanner = new RunnableScanner(true);
        runnableScanner.setPrintToConsole(true);
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(sigsLoader.getSignaturesMap());
        runnableScanner.setAllowedSignaturesMap(sigsLoader.getAllowedSignaturesMap());
        runnableScanner.setExcludeExtMap(sigsLoader.getExcludeExtsMap());
        runnableScanner.setDirToScan(repoPathToScan.toString());
        runnableScanner.setStagedFiles(stagedFiles);
        runnableScanner.run();

        // Step7: Analyze & Print results
        for (FoundPathItem foundPathItem : foundItemsContainer.getFoundItemsCopy()) {
            if (MiscUtils.isNotEmpty(foundPathItem.getFoundString())) {
                ConsoleUtils.warn("'{}' is found in file '{}' at line {}", foundPathItem.getFoundString(), foundPathItem.getFilePath(), foundPathItem.getLineNumber());
            }
        }

        ConsoleUtils.info("Scan is completed. Errors are " + (runnableScanner.isAnySignatureFound() ? "found :( Breaking commit!" : "not found :)"));

        if (runnableScanner.isAnySignatureFound()) {
            terminateAppWithErrorCode(false);
        }
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
}
