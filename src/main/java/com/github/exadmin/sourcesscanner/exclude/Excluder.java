package com.github.exadmin.sourcesscanner.exclude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.exadmin.sourcesscanner.fxui.helpers.AlertBuilder;
import com.github.exadmin.sourcesscanner.model.FoundPathItem;
import com.github.exadmin.sourcesscanner.model.ItemType;
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

    /**
     * Marks or unmark provided item as ignored and persists this state into special yaml-file.
     * For each item a root of git repository is tried to be defined. In case user works with non-git repositories
     * an exclusion configuration yaml-file will be created in the provided default path.
     * @param item FoundPathItem to be analyzed
     * @param defaultRootPath place to crete/write configuration file in case not git-repository will be defined for the provided item
     * @return Path to exclusion configuration file
     */
    public static Path markToExclude(FoundPathItem item, Path defaultRootPath) {
        // Get root of the repository for the provided item
        Path rootDir = getRepositoryRoot(item);
        if (rootDir == null) {
            rootDir = defaultRootPath;
        }

        // Load existed ignore-file
        Path excludesFile = Paths.get(rootDir.toString(), ".github", EXCLUDES_SHORT_FILE_NAME);
        ExcludeFileModel excludeFileModel = excludesFile.toFile().exists() ? loadExistedModel(excludesFile) : new ExcludeFileModel();
        if (excludeFileModel == null) {
            AlertBuilder.showError("Can't load existed exclusion configuration, please check logs and fix errors. If can't - then delete erroneous file.");
            return null;
        }

        // Mark/unmark exclusion
        item.setIgnored(!item.isIgnored());
        String relFileName = MiscUtils.getRelativeFileName(rootDir, item.getFilePath());
        String signature = item.getFoundString();

        String textHash = item.getType() == ItemType.SIGNATURE ? MiscUtils.getSHA256AsHex(signature) : "00000000";
        String fileHash = MiscUtils.getSHA256AsHex(relFileName);

        excludeFileModel.remove(textHash, fileHash);
        if (item.isIgnored()) excludeFileModel.add(textHash, fileHash);

        // save changes back to file
        excludesFile.toFile().getParentFile().mkdirs();
        try {
            excludeFileModel.doSortBeforeSaving();

            OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
            OBJECT_MAPPER.writeValue(excludesFile.toFile(), excludeFileModel);
        } catch (Exception ex) {
            log.error("Error while saving exclusions into the file {}", excludesFile, ex);
        }

        return excludesFile;
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
