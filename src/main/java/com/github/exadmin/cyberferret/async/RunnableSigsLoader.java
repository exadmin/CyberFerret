package com.github.exadmin.cyberferret.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reads all signatures and compiles them
 */
public class RunnableSigsLoader extends ARunnable {
    private static volatile Logger log = null;

    private final AtomicBoolean isReady = new AtomicBoolean(false);
    private Map<String, Pattern> regExpMap;           // map of signatures
    private Map<String, String> allowedSignaturesMap; // effectively the list of exact strings which are allowed when capturing
    private Map<String, List<String>> excludeExtsMap; // signature -> List of file extensions to ignore
    private String dictionaryVersion = "undefined";
    private InputStream inputStream;

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public Map<String, Pattern> getRegExpMap() {
        return regExpMap;
    }

    public Map<String, String> getAllowedSignaturesMap() {
        return allowedSignaturesMap;
    }

    public Map<String, List<String>> getExcludeExtsMap() {
        return excludeExtsMap;
    }

    public boolean isReady() {
        return isReady.get();
    }

    @Override
    public Logger getLog() {
        if (log == null) {
            log = LoggerFactory.getLogger(RunnableSigsLoader.class);
        }

        return log;
    }

    @Override
    public void _run() throws Exception {
        isReady.set(false);

        if (inputStream == null) throw new IllegalStateException("InputStream was not set before running action");

        Properties properties = new Properties();
        try (InputStream fis = inputStream) {
            properties.load(fis);

            Map<String, Pattern> regExpTmpMap = new HashMap<>();
            Map<String, String> allowedSignaturesTmpMap = new HashMap<>();
            Map<String, List<String>> includeExt = new HashMap<>();
            Map<String, List<String>> excludeExtTmpMap = new HashMap<>();

            for (Object key : properties.keySet()) {
                String sigId = key.toString();
                String expression = properties.getProperty(sigId);

                if ("version".equalsIgnoreCase(sigId)) {
                    dictionaryVersion = expression;
                    continue;
                }

                // if signature must exclude some files with provided list of extensions
                if (sigId.endsWith("(exclude-ext)")) {
                    String[] exts = expression.split(",");
                    List<String> extList = new ArrayList<>();
                    for (String ext : exts) {
                        ext = ext.trim();
                        if (!ext.isEmpty()) extList.add(ext);
                    }

                    sigId = sigId.substring(0, sigId.length() -13);
                    excludeExtTmpMap.put(sigId, extList);

                    continue;
                }

                if (sigId.endsWith("(allowed)")) {
                    sigId = sigId.substring(0, sigId.length() - 9);

                    // logInfo("Signature with id '{}' = '{}' is marked as allowed.", sigId, expression);
                    allowedSignaturesTmpMap.put(sigId, expression);
                } else {
                    compileAndKeep(sigId, expression, regExpTmpMap);
                }
            }

            regExpMap = Collections.unmodifiableMap(regExpTmpMap);
            allowedSignaturesMap = Collections.unmodifiableMap(allowedSignaturesTmpMap);
            excludeExtsMap = Collections.unmodifiableMap(excludeExtTmpMap);

            logInfo("Signatures are loaded successfully, number of signatures is {}", regExpMap.size());
            logInfo("Number of allowed signatures is {}", allowedSignaturesMap.size());
            logInfo("Dictionary version is {}", dictionaryVersion);

            isReady.set(true);
        } catch (IOException ex) {
            logError("Error while reading inoput stream with signatures");
        }
    }

    private static final Set<Character> SPECIAL_CHARS =
            Set.of('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '+', '=', '{', '}', '[', ']', '|', '\\', ':', ';', '"', '\'', '<', '>', ',', '.', '?', '/');

    private void compileAndKeep(String key, String regExpStr, Map<String, Pattern> map) {
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
            // logInfo("Compiling key '{}' effective expressions = '{}'", originalKeyName, regExpStr);
            Pattern regExp = Pattern.compile(regExpStr, Pattern.CASE_INSENSITIVE + Pattern.DOTALL);
            map.put(key, regExp);
        } catch (PatternSyntaxException pse) {
            logError("Error while compiling signature with ID = '{}', reg-exp = '{}'", originalKeyName, regExpStr);
        }
    }


}
