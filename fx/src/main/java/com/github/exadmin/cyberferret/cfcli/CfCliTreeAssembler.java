package com.github.exadmin.cyberferret.cfcli;

import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.github.exadmin.cyberferret.fxui.FxConstants.MAX_CONTEXT_FILE_SIZE_BYTES;

public final class CfCliTreeAssembler {
    private final Path root;
    private final FoundItemsContainer container;
    private final Consumer<String> warningSink;
    private final Map<String, FoundPathItem> pathItems = new HashMap<>();
    private Path cachedFile;
    private byte[] cachedContent;

    public CfCliTreeAssembler(Path root, FoundItemsContainer container, Consumer<String> warningSink) {
        this.root = root.toAbsolutePath().normalize();
        this.container = container;
        this.warningSink = warningSink;
    }

    public void accept(CfCliMessage message) throws IOException {
        switch (message.type()) {
            case "list" -> {
                if (message.folder() != null) {
                    ensureDirectory(normalizeRelative(message.folder()));
                } else {
                    ensureFile(normalizeRelative(message.file()));
                }
            }
            case "found", "allowed" -> addSignature(message);
            case "excluded" -> {
                if (message.isSignature()) {
                    addSignature(message);
                } else {
                    markPathExcluded(message.file());
                }
            }
            default -> throw new IOException("Unsupported Go CLI message type \"" + message.type() + "\"");
        }
    }

    private void addSignature(CfCliMessage message) throws IOException {
        String relativePath = normalizeRelative(message.file());
        FoundPathItem fileItem = ensureFile(relativePath);
        FoundPathItem signature = new FoundPathItem(fileItem.getFilePath(), ItemType.SIGNATURE, fileItem);
        signature.setVisualName(message.key());
        signature.setFoundString(message.found());
        signature.setAllowedValue("allowed".equals(message.type()));
        signature.setIgnored("excluded".equals(message.type()));
        try {
            FileMatchContext context = FileMatchContext.from(
                    contentFor(fileItem.getFilePath()), message.line(), message.found());
            signature.setLineNumber(context.lineNumber());
            signature.setDisplayText(context.displayText());
        } catch (IOException ex) {
            warningSink.accept("Cannot build context for \"" + fileItem.getFilePath() + "\": " + ex.getMessage());
        }
        container.addItem(signature);
    }

    private void markPathExcluded(String rawPath) throws IOException {
        String relativePath = normalizeRelative(rawPath);
        FoundPathItem item = pathItems.get(relativePath);
        if (item == null) throw new IOException("Excluded path was not listed first: " + rawPath);
        item.setIgnored(true);
        container.notifyItemUpdated(item);
    }

    private FoundPathItem ensureFile(String relativePath) throws IOException {
        FoundPathItem existing = pathItems.get(relativePath);
        if (existing != null) {
            if (existing.getType() != ItemType.FILE) throw new IOException("Path type changed to file: " + relativePath);
            return existing;
        }
        Path relative = Path.of(relativePath.replace('/', File.separatorChar));
        FoundPathItem parent = relative.getParent() == null
                ? null
                : ensureDirectory(toSlash(relative.getParent()));
        FoundPathItem item = new FoundPathItem(resolve(relativePath), ItemType.FILE, parent);
        pathItems.put(relativePath, item);
        container.addItem(item);
        return item;
    }

    private FoundPathItem ensureDirectory(String relativePath) throws IOException {
        FoundPathItem existing = pathItems.get(relativePath);
        if (existing != null) {
            if (existing.getType() != ItemType.DIRECTORY) {
                throw new IOException("Path type changed to directory: " + relativePath);
            }
            return existing;
        }
        Path relative = Path.of(relativePath.replace('/', File.separatorChar));
        FoundPathItem parent = relative.getParent() == null
                ? null
                : ensureDirectory(toSlash(relative.getParent()));
        FoundPathItem item = new FoundPathItem(resolve(relativePath), ItemType.DIRECTORY, parent);
        pathItems.put(relativePath, item);
        container.addItem(item);
        return item;
    }

    private String normalizeRelative(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) throw new IOException("Go CLI path is empty");
        Path relative = Path.of(rawPath.replace('/', File.separatorChar)).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Go CLI path escapes the scan root: " + rawPath);
        }
        String normalized = toSlash(relative);
        resolve(normalized);
        return normalized;
    }

    private Path resolve(String relativePath) throws IOException {
        Path resolved = root.resolve(relativePath.replace('/', File.separatorChar)).normalize();
        if (!resolved.startsWith(root)) throw new IOException("Go CLI path escapes the scan root: " + relativePath);
        return resolved;
    }

    /**
     * Loads a bounded file prefix for context extraction while preventing oversized allocations.
     * Consecutive signatures in the same file reuse the cached content.
     *
     * @param file file containing the reported signature
     * @return file content
     * @throws IOException when the file cannot be read
     */
    private byte[] contentFor(Path file) throws IOException {
        if (!file.equals(cachedFile)) {
            try (InputStream input = Files.newInputStream(file)) {
                cachedContent = input.readNBytes(MAX_CONTEXT_FILE_SIZE_BYTES);
            }
            cachedFile = file;
        }
        return cachedContent;
    }

    private static String toSlash(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }
}
