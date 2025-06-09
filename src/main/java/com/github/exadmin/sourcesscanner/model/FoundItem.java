package com.github.exadmin.sourcesscanner.model;

import java.nio.file.Path;

public class FoundItem {
    private Path path;
    private String visualName;
    private boolean isDirectory;
    private long startPlace;
    private long endPlace;
    private String signatureId;

    public FoundItem(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public String getVisualName() {
        return visualName;
    }

    public void setVisualName(String visualName) {
        this.visualName = visualName;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public long getStartPlace() {
        return startPlace;
    }

    public void setStartPlace(long startPlace) {
        this.startPlace = startPlace;
    }

    public long getEndPlace() {
        return endPlace;
    }

    public void setEndPlace(long endPlace) {
        this.endPlace = endPlace;
    }

    public String getSignatureId() {
        return signatureId;
    }

    public void setSignatureId(String signatureId) {
        this.signatureId = signatureId;
    }
}
