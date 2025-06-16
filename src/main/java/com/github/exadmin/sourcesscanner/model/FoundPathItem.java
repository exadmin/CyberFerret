package com.github.exadmin.sourcesscanner.model;

import javafx.beans.property.*;

import java.nio.file.Path;

public class FoundPathItem {
    private final Path filePath;
    private final FoundPathItem parent;
    private final StringProperty visualName = new SimpleStringProperty();
    private final ObjectProperty<ItemType> type = new SimpleObjectProperty<>();
    private final LongProperty lineNumber = new SimpleLongProperty();
    private final StringProperty displayText = new SimpleStringProperty(); // this text will be shown in the report including all chars around
    private final StringProperty foundString = new SimpleStringProperty(); // this is exactly found signature
    private final BooleanProperty ignored = new SimpleBooleanProperty();

    public FoundPathItem(Path filePath, ItemType type, FoundPathItem parent) {
        this.parent = parent;
        this.filePath = filePath;
        this.visualName.setValue(filePath.getFileName().toString());
        this.type.setValue(type);
        this.lineNumber.setValue(0);
        this.displayText.setValue("");
    }

    public FoundPathItem getParent() {
        return parent;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getVisualName() {
        return visualName.get();
    }

    public StringProperty visualNameProperty() {
        return visualName;
    }

    public void setVisualName(String visualName) {
        this.visualName.set(visualName);
    }

    public ItemType getType() {
        return type.get();
    }

    public ObjectProperty<ItemType> typeProperty() {
        return type;
    }

    public void setType(ItemType type) {
        this.type.set(type);
    }

    public long getLineNumber() {
        return lineNumber.get();
    }

    public LongProperty lineNumberProperty() {
        return lineNumber;
    }

    public void setLineNumber(long lineNumber) {
        this.lineNumber.set(lineNumber);
    }

    public String getDisplayText() {
        return displayText.get();
    }

    public StringProperty displayTextProperty() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText.set(displayText);
    }

    public boolean isIgnored() {
        return ignored.get();
    }

    public BooleanProperty ignoredProperty() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored.set(ignored);
    }

    public String getFoundString() {
        return foundString.get();
    }

    public StringProperty foundStringProperty() {
        return foundString;
    }

    public void setFoundString(String foundString) {
        this.foundString.set(foundString);
    }

    @Override
    public String toString() {
        return filePath.toString();
    }
}
