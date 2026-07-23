package com.github.exadmin.cyberferret.model;

public enum ItemType {
    DIRECTORY(1),
    FILE (2),
    SIGNATURE (3);

    private final int sortOrder;

    ItemType(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
