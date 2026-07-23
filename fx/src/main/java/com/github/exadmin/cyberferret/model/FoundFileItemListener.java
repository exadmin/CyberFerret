package com.github.exadmin.cyberferret.model;

public interface FoundFileItemListener {
    void newItemAdded(FoundPathItem newItem);

    default void newItemAdded(FoundPathItem newItem, long generation) {
        newItemAdded(newItem);
    }

    default void itemUpdated(FoundPathItem item) {
    }

    default void itemUpdated(FoundPathItem item, long generation) {
        itemUpdated(item);
    }

    void onClearAll();

    default void onClearAll(long generation) {
        onClearAll();
    }
}
