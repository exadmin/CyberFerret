package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.utils.ConsoleUtils;
import com.github.exadmin.cyberferret.utils.FileUtils;

import java.nio.file.Path;

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
        // step1: Check required program arguments are set
        // step2: Check required system env variables are set
        // step2: Ensure actual dictionary is downloaded
        // step3: Download dictionary if required
        // step4: Decrypt dictionary
        // step5: Run check over the git-repository


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

        // step2: Check required system env variables are set
        final String pass = System.getenv(SYS_ENV_VAR_PASSWORD);
        if (pass == null || pass.isEmpty()) {
            ConsoleUtils.error("Missing environment variable {}", SYS_ENV_VAR_PASSWORD);
            printUsage();
            terminateAppWithErrorCode();
        }

        ConsoleUtils.debug("Scan is fakely completed");
    }
}
