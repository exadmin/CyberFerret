package com.github.exadmin.cyberferret.cfcli;

import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class CfCliExecutable {
    private static final String DEFAULT_COMMAND = "cfcli";

    private final String configuredPath;

    public CfCliExecutable(String configuredPath) {
        this.configuredPath = configuredPath == null ? "" : configuredPath;
    }

    public String command() {
        return configuredPath.isBlank() ? DEFAULT_COMMAND : configuredPath;
    }

    public Optional<String> validationError() {
        if (configuredPath.isBlank()) {
            return Optional.empty();
        }
        try {
            if (Files.isRegularFile(Path.of(configuredPath))) {
                return Optional.empty();
            }
        } catch (InvalidPathException ignored) {
            // Report invalid paths through the same user-facing validation message.
        }
        return Optional.of("CF CLI executable must be an existing regular file: " + configuredPath);
    }
}
