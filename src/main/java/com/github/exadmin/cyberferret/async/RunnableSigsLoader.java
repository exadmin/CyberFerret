package com.github.exadmin.cyberferret.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reads all signatures and compiles them
 */
public class RunnableSigsLoader extends ARunnable {
    private static final Logger log = LoggerFactory.getLogger(RunnableSigsLoader.class);

    private Path signaturesFile;
    private final AtomicBoolean isReady = new AtomicBoolean(false);
    private Map<String, Pattern> regExpMap;
    private Map<String, String> allowedSignaturesMap;
    private String dictionaryVersion = "undefined";

    public void setFileToLoad(Path filePath) {
        File file = filePath.toFile();
        if (file.isFile() && file.exists()) {
            this.signaturesFile = filePath;
            return;
        }

        throw new IllegalStateException("Can't find file " + signaturesFile);
    }

    public Map<String, Pattern> getRegExpMap() {
        return regExpMap;
    }

    public Map<String, String> getAllowedSignaturesMap() {
        return allowedSignaturesMap;
    }

    public boolean isReady() {
        return isReady.get();
    }

    @Override
    protected Logger getLog() {
        return log;
    }

    @Override
    public void _run() throws Exception {
        isReady.set(false);

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(signaturesFile.toFile())) {
            properties.load(fis);

            Map<String, Pattern> expMap = new HashMap<>();
            Map<String, String> allowedMap = new HashMap<>();
            for (Object key : properties.keySet()) {
                String sigId = key.toString();
                String expression = properties.getProperty(sigId);

                if ("version".equalsIgnoreCase(sigId)) {
                    dictionaryVersion = expression;
                    continue;
                }

                if (sigId.endsWith("(allowed)")) {
                    sigId = sigId.substring(0, sigId.length() - 9);

                    log.info("Signature with id '{}' = '{}' is marked as allowed.", sigId, expression);
                    allowedMap.put(sigId, expression);
                } else {
                    compileAndKeep(sigId, expression, expMap);
                }
            }

            regExpMap = Collections.unmodifiableMap(expMap);
            allowedSignaturesMap = Collections.unmodifiableMap(allowedMap);

            log.info("Signatures are loaded successfully from {}. Number of signatures is {}", signaturesFile, regExpMap.size());
            log.info("Number of allowed signatures is {}", allowedSignaturesMap.size());
            log.info("Dictionary version is {}", dictionaryVersion);

            isReady.set(true);
        } catch (IOException ex) {
            log.error("Error while reading file {}", signaturesFile);
        }
    }

    private static final Set<Character> SPECIAL_CHARS =
            Set.of('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '+', '=', '{', '}', '[', ']', '|', '\\', ':', ';', '"', '\'', '<', '>', ',', '.', '?', '/');

    private static void compileAndKeep(String key, String regExpStr, Map<String, Pattern> map) {
        final String originalKeyName = key; // for logging aims

        if (key.endsWith("(regexp)")) {
            key = key.substring(0, key.length() - 8);
        } else if (!key.contains("(") && !key.contains(")")) {

            // escape special characters if exists
            StringBuilder sb = new StringBuilder();
            for (char ch : regExpStr.toCharArray()) {
                for (char specialCh : SPECIAL_CHARS) {
                    if (ch == specialCh) {
                        sb.append("\\");
                        break;
                    }
                }

                sb.append(ch);
            }

            regExpStr = "\\b" + sb + "\\b";
        }

        try {
            log.info("Compiling key '{}' effective expressions = '{}'", originalKeyName, regExpStr);
            Pattern regExp = Pattern.compile(regExpStr, Pattern.CASE_INSENSITIVE + Pattern.DOTALL);
            map.put(key, regExp);
        } catch (PatternSyntaxException pse) {
            log.error("Error while compiling signature with ID = '{}', reg-exp = '{}'", originalKeyName, regExpStr);
        }
    }
}
