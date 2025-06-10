package com.github.exadmin.sourcesscanner.model;

import javafx.beans.property.*;

import java.nio.file.Path;

public class FoundItem {
    private Path path;
    private StringProperty visualName = new SimpleStringProperty();
    private BooleanProperty isDirectory = new SimpleBooleanProperty();
    private LongProperty startPlace = new SimpleLongProperty();
    private LongProperty endPlace = new SimpleLongProperty();
    private StringProperty signatureId = new SimpleStringProperty();

    public FoundItem(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
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
