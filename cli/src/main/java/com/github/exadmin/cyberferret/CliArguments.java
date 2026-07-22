package com.github.exadmin.cyberferret;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record CliArguments(
        Command command,
        Path repository,
        Path stagedFilesList,
        Path cacheDirectory,
        boolean offline) {
    private static final String QUICK_MODE_OPTION = "--mode=quick";
    private static final String DICTIONARY_VERSION_OPTION = "--dictionary-version";
    private static final String OFFLINE_OPTION = "--offline";
    private static final String CACHE_DIRECTORY_PREFIX = "--cache-dir=";

    enum Command {
        DETAILED_SCAN,
        QUICK_SCAN,
        DICTIONARY_VERSION
    }

    static CliArguments parse(String[] args) {
        Command command = Command.DETAILED_SCAN;
        boolean explicitCommand = false;
        boolean offline = false;
        Path cacheDirectory = null;
        List<String> positional = new ArrayList<>();

        for (String argument : args) {
            if (QUICK_MODE_OPTION.equals(argument)) {
                if (explicitCommand) throw invalidArguments();
                command = Command.QUICK_SCAN;
                explicitCommand = true;
            } else if (DICTIONARY_VERSION_OPTION.equals(argument)) {
                if (explicitCommand) throw invalidArguments();
                command = Command.DICTIONARY_VERSION;
                explicitCommand = true;
            } else if (OFFLINE_OPTION.equals(argument)) {
                if (offline) throw invalidArguments();
                offline = true;
            } else if (argument.startsWith(CACHE_DIRECTORY_PREFIX)) {
                if (cacheDirectory != null) throw invalidArguments();
                String value = argument.substring(CACHE_DIRECTORY_PREFIX.length());
                if (value.isEmpty()) throw invalidArguments();
                cacheDirectory = Path.of(value).normalize();
            } else if (argument.startsWith("--")) {
                throw invalidArguments();
            } else {
                positional.add(argument);
            }
        }

        return switch (command) {
            case DETAILED_SCAN -> parseDetailed(positional, cacheDirectory, offline);
            case QUICK_SCAN -> parseQuick(positional, cacheDirectory, offline);
            case DICTIONARY_VERSION -> parseDictionaryVersion(positional, cacheDirectory, offline);
        };
    }

    private static CliArguments parseDetailed(
            List<String> positional,
            Path cacheDirectory,
            boolean offline) {
        if (positional.size() < 1 || positional.size() > 2 || cacheDirectory != null || offline) {
            throw invalidArguments();
        }
        return new CliArguments(
                Command.DETAILED_SCAN,
                Path.of(positional.get(0)).normalize(),
                positional.size() == 2 ? Path.of(positional.get(1)).normalize() : null,
                null,
                false);
    }

    private static CliArguments parseQuick(
            List<String> positional,
            Path cacheDirectory,
            boolean offline) {
        if (positional.size() != 1) throw invalidArguments();
        return new CliArguments(
                Command.QUICK_SCAN,
                Path.of(positional.getFirst()).normalize(),
                null,
                cacheDirectory,
                offline);
    }

    private static CliArguments parseDictionaryVersion(
            List<String> positional,
            Path cacheDirectory,
            boolean offline) {
        if (!positional.isEmpty() || offline) throw invalidArguments();
        return new CliArguments(Command.DICTIONARY_VERSION, null, null, cacheDirectory, false);
    }

    private static IllegalArgumentException invalidArguments() {
        return new IllegalArgumentException("Invalid command-line arguments.");
    }
}
