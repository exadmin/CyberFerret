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
    private Map<String, Pattern> regExpMap;           // map of signatures
    private Map<String, String> allowedSignaturesMap; // effectively the list of exact strings which are allowed when capturing
    private Map<String, List<String>> excludeExtsMap; // signature -> List of file extensions to ignore
    private List<Pattern> binaryExcludePatterns;       // list of file name patterns to exclude from binary detection
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

    public Map<String, List<String>> getExcludeExtsMap() {
        return excludeExtsMap;
    }

    public List<Pattern> getBinaryExcludePatterns() {
        return binaryExcludePatterns;
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

            Map<String, Pattern> regExpTmpMap = new HashMap<>();
            Map<String, String> allowedSignaturesTmpMap = new HashMap<>();
            Map<String, List<String>> includeExt = new HashMap<>();
            Map<String, List<String>> excludeExtTmpMap = new HashMap<>();
            List<Pattern> binaryExcludePatternsTmp = new ArrayList<>();

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

                // load binary file exclusion patterns
                if (sigId.endsWith("(binary-exclude)")) {
                    String[] patterns = expression.split(",");
                    for (String patternStr : patterns) {
                        patternStr = patternStr.trim();
                        if (!patternStr.isEmpty()) {
                            try {
                                Pattern pattern = Pattern.compile(patternStr);
                                binaryExcludePatternsTmp.add(pattern);
                                log.info("Binary exclusion pattern loaded: '{}'", patternStr);
                            } catch (PatternSyntaxException pse) {
                                log.error("Error while compiling binary exclusion pattern '{}'", patternStr, pse);
                            }
                        }
                    }

                    continue;
                }

                if (sigId.endsWith("(allowed)")) {
                    sigId = sigId.substring(0, sigId.length() - 9);

                    log.info("Signature with id '{}' = '{}' is marked as allowed.", sigId, expression);
                    allowedSignaturesTmpMap.put(sigId, expression);
                } else {
                    compileAndKeep(sigId, expression, regExpTmpMap);
                }
            }

            regExpMap = Collections.unmodifiableMap(regExpTmpMap);
            allowedSignaturesMap = Collections.unmodifiableMap(allowedSignaturesTmpMap);
            excludeExtsMap = Collections.unmodifiableMap(excludeExtTmpMap);
            binaryExcludePatterns = Collections.unmodifiableList(binaryExcludePatternsTmp);

            log.info("Signatures are loaded successfully from {}. Number of signatures is {}", signaturesFile, regExpMap.size());
            log.info("Number of allowed signatures is {}", allowedSignaturesMap.size());
            log.info("Number of binary exclusion patterns is {}", binaryExcludePatterns.size());
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
