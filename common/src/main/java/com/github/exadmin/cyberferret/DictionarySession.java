package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.async.RunnableCheckOnlineDictionary;
import com.github.exadmin.cyberferret.async.RunnableSigsLoader;
import com.github.exadmin.cyberferret.utils.FileUtils;
import com.github.exadmin.cyberferret.utils.PasswordBasedEncryption;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DictionarySession {
    private static final Pattern VALID_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,63}");
    private final Map<String, Pattern> signaturesMap;
    private final Map<String, String> allowedSignaturesMap;
    private final Map<String, List<String>> excludeExtsMap;
    private final String dictionaryVersion;

    private DictionarySession(RunnableSigsLoader loader) {
        signaturesMap = loader.getSignaturesMap();
        allowedSignaturesMap = loader.getAllowedSignaturesMap();
        excludeExtsMap = loader.getExcludeExtsMap();
        String loadedVersion = loader.getDictionaryVersion();
        String normalizedVersion = loadedVersion == null
                || loadedVersion.isBlank()
                || "undefined".equalsIgnoreCase(loadedVersion)
                ? "unknown"
                : loadedVersion.trim();
        if (!VALID_VERSION.matcher(normalizedVersion).matches()) {
            throw new DictionaryException("Dictionary version is invalid.");
        }
        dictionaryVersion = normalizedVersion;
    }

    public static DictionarySession prepare(Path cacheDirectory, boolean offline, String password) {
        if (password == null || password.isBlank()) {
            throw new DictionaryException("Dictionary password is unavailable.");
        }

        RunnableCheckOnlineDictionary downloader = new RunnableCheckOnlineDictionary(true);
        downloader.setSilent(true);
        downloader.setCacheDirectory(cacheDirectory);
        Path dictionaryPath = downloader.getDictionaryPath();

        if (!offline) {
            createCacheDirectory(dictionaryPath);
            downloader.run();
        }

        if (!Files.isRegularFile(dictionaryPath)) {
            throw new DictionaryException("Cached dictionary is unavailable.");
        }

        String encryptedBody;
        try {
            encryptedBody = FileUtils.readFile(dictionaryPath);
        } catch (Exception exception) {
            throw new DictionaryException("Cannot read the cached dictionary.");
        }

        String decryptedBody;
        try {
            decryptedBody = PasswordBasedEncryption.decrypt(encryptedBody, password);
        } catch (Exception exception) {
            throw new DictionaryException("Cannot decrypt the dictionary.");
        }

        RunnableSigsLoader loader = new RunnableSigsLoader(true);
        loader.setSilent(true);
        loader.setInputStream(new ByteArrayInputStream(decryptedBody.getBytes(StandardCharsets.UTF_8)));
        try {
            loader._run();
        } catch (Exception exception) {
            throw new DictionaryException("Cannot parse the dictionary.");
        }
        if (!loader.isReady()) {
            throw new DictionaryException("Cannot parse the dictionary.");
        }
        return new DictionarySession(loader);
    }

    private static void createCacheDirectory(Path dictionaryPath) {
        Path parent = dictionaryPath.toAbsolutePath().getParent();
        if (parent == null) return;
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new DictionaryException("Cannot create the dictionary cache directory.");
        }
    }

    public Map<String, Pattern> signaturesMap() {
        return signaturesMap;
    }

    public Map<String, String> allowedSignaturesMap() {
        return allowedSignaturesMap;
    }

    public Map<String, List<String>> excludeExtsMap() {
        return excludeExtsMap;
    }

    public String dictionaryVersion() {
        return dictionaryVersion;
    }

    public static final class DictionaryException extends IllegalStateException {
        public DictionaryException(String message) {
            super(message);
        }
    }
}
