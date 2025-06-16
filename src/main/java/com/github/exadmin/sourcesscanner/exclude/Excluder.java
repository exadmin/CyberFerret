package com.github.exadmin.sourcesscanner.exclude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.exadmin.sourcesscanner.fxui.helpers.AlertBuilder;
import com.github.exadmin.sourcesscanner.model.FoundPathItem;
import com.github.exadmin.sourcesscanner.utils.MiscUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Excluder {
    private static final Logger log = LoggerFactory.getLogger(Excluder.class);
    public static final String EXCLUDES_SHORT_FILE_NAME = "qs-grand-report.yaml";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void markToExclude(FoundPathItem item) {
        // Get root of the repository for the provided item
        final Path rootDir = getRepositoryRoot(item);
        if (rootDir == null) {
            log.error("Can't mark {} as ignored, cause can't define if it's placed inside Git-repository", item);
            return;
        }

        // Load existed ignore-file
        Path excludesFile = Paths.get(rootDir.toString(), ".github", EXCLUDES_SHORT_FILE_NAME);
        ExcludeFileModel excludeFileModel = excludesFile.toFile().exists() ? loadExistedModel(excludesFile) : new ExcludeFileModel();
        if (excludeFileModel == null) {
            AlertBuilder.showError("Can't load existed exclusion configuration, please check logs and fix errors. If can't - then delete erroneous file.");
            return;
        }

        // Mark/unmark exclusion
        item.setIgnored(!item.isIgnored());
        String relFileName = MiscUtils.getRelativeFileName(rootDir, item.getFilePath());
        String signature = item.getFoundString();

        String textHash = MiscUtils.getSHA256AsHex(signature);
        String fileHash = MiscUtils.getSHA256AsHex(relFileName);

        ExcludeSignatureItem newExcludeItem = new ExcludeSignatureItem();
        newExcludeItem.setTextHash(textHash);
        newExcludeItem.setFileHash(fileHash);

        ExcludeSignatureItem existedExcludeItem = null;
        for (ExcludeSignatureItem next : excludeFileModel.getSignatures()) {
            if (next.equals(newExcludeItem)) {
                existedExcludeItem = next;
                break;
            }
        }

        excludeFileModel.getSignatures().remove(existedExcludeItem);
        if (item.isIgnored()) {
            excludeFileModel.getSignatures().add(newExcludeItem);
        }

        // save changes back to file
        excludesFile.toFile().getParentFile().mkdirs();
        try {
            OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
            OBJECT_MAPPER.writeValue(excludesFile.toFile(), excludeFileModel);
        } catch (Exception ex) {
            log.error("Error while saving exclusions into the file {}", excludesFile, ex);
        }
    }

    private static ExcludeFileModel loadExistedModel(Path filePath) {
        try {
            OBJECT_MAPPER.enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature());
            return OBJECT_MAPPER.readValue(filePath.toFile(), ExcludeFileModel.class);
        } catch (Exception ex) {
            log.error("Can't load existed exclusion configuration {}", filePath, ex);
            return null;
        }
    }

    private static Path getRepositoryRoot(FoundPathItem item) {
        Path path = item.getFilePath();
        while (path != null) {
            File dir = path.toFile();

            if (dir.isFile()) {
                path = path.getParent();
                continue;
            }

            // check if we have ".git" folder here
            String[] children = dir.list();
            if (children != null) {
                for (String next : children) {
                    if (".git".equals(next)) return path;
                }
            }

            path = path.getParent();
        }

        return null;
    }


}
