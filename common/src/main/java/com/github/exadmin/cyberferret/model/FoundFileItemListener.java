package com.github.exadmin.cyberferret.model;

public interface FoundFileItemListener {
    void newItemAdded(FoundPathItem newItem);

    default void newItemAdded(FoundPathItem newItem, long generation) {
        newItemAdded(newItem);
    }

    void onClearAll();

    default void onClearAll(long generation) {
        onClearAll();
    }
}
