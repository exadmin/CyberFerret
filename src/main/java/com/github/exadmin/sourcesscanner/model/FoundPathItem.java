package com.github.exadmin.sourcesscanner.model;

import javafx.beans.property.*;

import java.nio.file.Path;

public class FoundPathItem {
    public static final String STATUS_FOUND = "found";
    public static final String STATUS_ANALIZED_ALL_CLEAR = "clear";
    public static final String STATUS_ANALIZED_WARN = "warning";

    private Path filePath;
    private FoundPathItem parent;
    private StringProperty visualName = new SimpleStringProperty();
    private StringProperty status = new SimpleStringProperty();
    private ObjectProperty<ItemType> type = new SimpleObjectProperty<>();
    private StringProperty signatureId = new SimpleStringProperty();
    private LongProperty lineNumber = new SimpleLongProperty();
    private StringProperty text = new SimpleStringProperty();
    private BooleanProperty ignored = new SimpleBooleanProperty();

    public FoundPathItem(Path filePath, ItemType type, FoundPathItem parent) {
        this.parent = parent;
        this.filePath = filePath;
        this.visualName.setValue(filePath.getFileName().toString());
        this.status.setValue(STATUS_FOUND);
        this.type.setValue(type);
        this.signatureId.setValue("");
        this.lineNumber.setValue(0);
        this.text.setValue("");
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

    public String getStatus() {
        return status.get();
    }

    public StringProperty statusProperty() {
        return status;
    }

    public void setStatus(String status) {
        this.status.set(status);
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

    public String getSignatureId() {
        return signatureId.get();
    }

    public StringProperty signatureIdProperty() {
        return signatureId;
    }

    public void setSignatureId(String signatureId) {
        this.signatureId.set(signatureId);
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

    public String getText() {
        return text.get();
    }

    public StringProperty textProperty() {
        return text;
    }

    public void setText(String text) {
        this.text.set(text);
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
}
