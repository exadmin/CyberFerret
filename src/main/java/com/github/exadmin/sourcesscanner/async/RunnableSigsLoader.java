package com.github.exadmin.sourcesscanner.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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

            Map<String, Pattern> map = new HashMap<>();
            for (Object key : properties.keySet()) {
                String sigId = key.toString();
                String regExpStr = properties.getProperty(sigId);

                try {
                    Pattern regExp = Pattern.compile(regExpStr, Pattern.CASE_INSENSITIVE + Pattern.DOTALL);
                    map.put(sigId, regExp);
                } catch (PatternSyntaxException pse) {
                    log.error("Error while compiling signature with ID = '{}', reg-exp = '{}'", sigId, regExpStr);
                }
            }

            regExpMap = Collections.unmodifiableMap(map);
            log.info("Signatures are loaded successfully from {}. Number of signatures is {}", signaturesFile, regExpMap.size());

            isReady.set(true);
        } catch (IOException ex) {
            log.error("Error while reading file {}", signaturesFile);
        }
    }
}
