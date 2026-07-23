package com.github.exadmin.cyberferret.model;

import java.nio.file.Path;

public class FoundPathItem {
    private final Path filePath;
    private final FoundPathItem parent;
    private String visualName;
    private ItemType type;
    private long lineNumber;
    private String displayText; // this text will be shown in the report including all chars around
    private String foundString; // this is exactly found signature
    private boolean ignored;
    private boolean allowedValue;

    public FoundPathItem(Path filePath, ItemType type, FoundPathItem parent) {
        this.parent = parent;
        this.filePath = filePath;
        this.visualName = filePath.getFileName() == null ? filePath.toString() : filePath.getFileName().toString();
        this.type = type;
        this.lineNumber = 0;
        this.displayText = "";
        this.foundString = "";
        this.allowedValue = false;
    }

    public FoundPathItem getParent() {
        return parent;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getVisualName() {
        return visualName;
    }

    public void setVisualName(String visualName) {
        this.visualName = visualName;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(long lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public String getFoundString() {
        return foundString;
    }

    public void setFoundString(String foundString) {
        this.foundString = foundString;
    }

    @Override
    public String toString() {
        return filePath.toString();
    }

    public boolean isAllowedValue() {
        return allowedValue;
    }

    public void setAllowedValue(boolean newValue) {
        this.allowedValue = newValue;
    }
}
