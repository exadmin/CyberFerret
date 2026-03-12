package com.github.exadmin.cyberferret.exclude;

import com.github.exadmin.cyberferret.logging.LoggerProxy;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import com.github.exadmin.cyberferret.utils.FileUtils;
import com.github.exadmin.cyberferret.utils.MiscUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Excluder {
    private static final LoggerProxy LOG = new LoggerProxy(Excluder.class);
    public static final String PERSISTENCE_FOLDER = ".qubership";
    public static final String EXCLUDES_SHORT_FILE_NAME = "grand-report.json";
    public static final String HASH_IGNORE_CONTENT = "00000000";

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
        Path excludesFile = Paths.get(rootDir.toString(), Excluder.PERSISTENCE_FOLDER, EXCLUDES_SHORT_FILE_NAME);
        ExcludeFileModel excludeFileModel = getModelToWorkWith(excludesFile);
        if (excludeFileModel == null) {
            LOG.error("Can't load existed exclusion configuration, please check logs and fix errors. If can't - then delete erroneous file.");
            return null;
        }

        // Mark/unmark exclusion
        item.setIgnored(!item.isIgnored());
        String relFileName = MiscUtils.getRelativeFileName(rootDir, item.getFilePath());
        String signature = item.getFoundString();

        String textHash = item.getType() == ItemType.SIGNATURE ? MiscUtils.getSHA256AsHex(signature) : HASH_IGNORE_CONTENT;
        String fileHash = MiscUtils.getSHA256AsHex(relFileName);

        excludeFileModel.remove(textHash, fileHash);
        if (item.isIgnored()) excludeFileModel.add(textHash, fileHash);

        // save changes back to file
        excludesFile.toFile().getParentFile().mkdirs();
        try {
            excludeFileModel.doSortBeforeSaving();
            String text = ExcludeFileJsonCodec.toJson(excludeFileModel) + "\n";
            FileUtils.saveToFile(text, excludesFile.toString());
        } catch (Exception ex) {
            LOG.error("Error while saving exclusions into the file {}", excludesFile, ex);
        }

        return excludesFile;
    }

    /**
     * Tries to load existed model with exclusions. If file does not exist - it is similar to empty configuration,
     * so new empty instance will be returned.
     * In case of any error during loading process (for instance incorrect file format) a null will be returned.
     * @param filePath Path to configuration file to load model from
     * @return
     */
    private static ExcludeFileModel getModelToWorkWith(Path filePath) {
        try {
            File file = filePath.toFile();
            if (file.exists() && file.isFile()) {
                return ExcludeFileJsonCodec.fromJson(FileUtils.readFile(filePath));
            } else {
                return new ExcludeFileModel();
            }
        } catch (Exception ex) {
            LOG.error("Error happened during configuration loading from {}", filePath, ex);
        }

        return null;
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
