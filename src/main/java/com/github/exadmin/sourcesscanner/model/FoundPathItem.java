package com.github.exadmin.sourcesscanner.model;

import javafx.beans.property.*;

import java.nio.file.Path;

public class FoundPathItem {
    public static final String STATUS_FOUND = "found";

    private Path filePath;
    private StringProperty visualName = new SimpleStringProperty();
    private StringProperty status = new SimpleStringProperty();
    private BooleanProperty isDirectory = new SimpleBooleanProperty();
    private LongProperty startPlace = new SimpleLongProperty();
    private LongProperty endPlace = new SimpleLongProperty();
    private StringProperty signatureId = new SimpleStringProperty();

    public FoundPathItem(Path filePath) {
        this.filePath = filePath;
        this.visualName.setValue(filePath.getFileName().toString());
        this.status.setValue(STATUS_FOUND);
        this.isDirectory.setValue(filePath.toFile().isDirectory());
        this.startPlace.setValue(0);
        this.startPlace.setValue(0);
        this.signatureId.setValue("");
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

    public boolean isIsDirectory() {
        return isDirectory.get();
    }

    public BooleanProperty isDirectoryProperty() {
        return isDirectory;
    }

    public void setIsDirectory(boolean isDirectory) {
        this.isDirectory.set(isDirectory);
    }

    public long getStartPlace() {
        return startPlace.get();
    }

    public LongProperty startPlaceProperty() {
        return startPlace;
    }

    public void setStartPlace(long startPlace) {
        this.startPlace.set(startPlace);
    }

    public long getEndPlace() {
        return endPlace.get();
    }

    public LongProperty endPlaceProperty() {
        return endPlace;
    }

    public void setEndPlace(long endPlace) {
        this.endPlace.set(endPlace);
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
}
