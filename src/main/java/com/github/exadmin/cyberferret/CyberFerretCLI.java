package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.async.RunnableCheckOnlineDictionary;
import com.github.exadmin.cyberferret.async.RunnableScanner;
import com.github.exadmin.cyberferret.async.RunnableSigsLoader;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.utils.ConsoleUtils;
import com.github.exadmin.cyberferret.utils.FileUtils;
import com.github.exadmin.cyberferret.utils.MiscUtils;
import com.github.exadmin.cyberferret.utils.PasswordBasedEncryption;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static com.github.exadmin.cyberferret.fxui.FxConstants.DICTIONARY_FILE_PATH_ENCRYPTED;

/**
 * This is CLI version of CyberFerret app with focus on quick initialization and run triggered by pre-commit framework.
 */
public class CyberFerretCLI {
    private static final String SYS_ENV_VAR_PASSWORD = "CYBER_FERRET_PASSWORD";

    private static void printUsage() {
        String errMsg = """
                    Usage: CyberFerretCLI $PATH_TO_REPOSITORY_TO_SCAN"
                    Also, not that '{}' System Environment variable must be set
                    """;
        errMsg = ConsoleUtils.format(errMsg, SYS_ENV_VAR_PASSWORD);
        System.out.println(errMsg);
    }

    private static void terminateAppWithErrorCode() {
        System.exit(1);
    }

    public static void main(String[] args) {
        // Overall logic
        // Step1: Check required program arguments are set
        // Step2: Check required system env variables are set
        // Step3: Ensure actual dictionary is downloaded
        // Step4: Download dictionary if required
        // Step5: Decrypt dictionary
        // Step6: Run check over the git-repository


        // Step1: Check required program arguments are set
        if (args.length < 1) {
            ConsoleUtils.error("Missing command line argument");
            printUsage();
            terminateAppWithErrorCode();
        }

        final Path rootPathToScan = Path.of(args[0]);
        if (!FileUtils.isPathToDir(rootPathToScan)) {
            ConsoleUtils.error("Invalid path to scan directory {}", rootPathToScan);
            printUsage();
            terminateAppWithErrorCode();
        }

        // Step2: Check required system env variables are set
        final String pass = System.getenv(SYS_ENV_VAR_PASSWORD);
        if (pass == null || pass.isEmpty()) {
            ConsoleUtils.error("Missing environment variable {}", SYS_ENV_VAR_PASSWORD);
            printUsage();
            terminateAppWithErrorCode();
        }

        // Step3: Ensure actual dictionary is downloaded
        RunnableCheckOnlineDictionary dictionaryDownloader = new RunnableCheckOnlineDictionary();
        dictionaryDownloader.setPrintToConsole(true);
        dictionaryDownloader.run();

        // Step5:
        RunnableSigsLoader sigsLoader = new RunnableSigsLoader();
        sigsLoader.setPrintToConsole(true);
        try {
            String encryptedBody = FileUtils.readFile(DICTIONARY_FILE_PATH_ENCRYPTED);
            String decryptedBody = PasswordBasedEncryption.decrypt(encryptedBody, pass);

            byte[] bytes = decryptedBody.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(bytes);
            sigsLoader.setInputStream(inputStream);
            sigsLoader.run();
        } catch (Exception ex) {
            ConsoleUtils.error("Error while loading signatures. " + ex.getMessage());
            terminateAppWithErrorCode();
        }

        // Step6: Run scanner
        FoundItemsContainer foundItemsContainer = new FoundItemsContainer();

        RunnableScanner runnableScanner = new RunnableScanner();
        runnableScanner.setCLIMode(true);
        runnableScanner.setPrintToConsole(true);
        runnableScanner.setFoundItemsContainer(foundItemsContainer);
        runnableScanner.setSignaturesMap(sigsLoader.getSignaturesMap());
        runnableScanner.setAllowedSignaturesMap(sigsLoader.getAllowedSignaturesMap());
        runnableScanner.setExcludeExtMap(sigsLoader.getExcludeExtsMap());
        runnableScanner.setDirToScan(rootPathToScan.toString());
        runnableScanner.run();

        // Step7: Analyze & Print results

        for (FoundPathItem foundPathItem : foundItemsContainer.getFoundItemsCopy()) {
            if (MiscUtils.isNotEmpty(foundPathItem.getFoundString())) {
                ConsoleUtils.warn("'{}' is found in file '{}' at line {}", foundPathItem.getFoundString(), foundPathItem.getFilePath(), foundPathItem.getLineNumber());
            }
        }

        ConsoleUtils.debug("Scan is completed. Errors are " + (runnableScanner.isAnySignatureFound() ? "found \uD83D\uDC4E" : "not found \uD83D\uDC4D"));

        if (runnableScanner.isAnySignatureFound()) {
            terminateAppWithErrorCode();
        }
    }
}
