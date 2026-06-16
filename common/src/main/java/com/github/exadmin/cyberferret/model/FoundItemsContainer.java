package com.github.exadmin.cyberferret.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FoundItemsContainer {
    private final List<FoundPathItem> foundPathItems = Collections.synchronizedList(new ArrayList<>());
    private volatile FoundFileItemListener onAddNewItemListener;
    private long generation;

    public void addItem(FoundPathItem newItem) {
        long itemGeneration;
        synchronized (foundPathItems) {
            foundPathItems.add(newItem);
            itemGeneration = generation;
        }

        FoundFileItemListener listener = onAddNewItemListener;
        if (listener != null) listener.newItemAdded(newItem, itemGeneration);
    }

    // do not public this api
    private List<FoundPathItem> getFoundItems() {
        return foundPathItems;
    }

    public int getFoundItemsSize() {
        synchronized (foundPathItems) {
            return foundPathItems.size();
        }
    }

    public List<FoundPathItem> getFoundItemsCopy() {
        synchronized (foundPathItems) {
            return new ArrayList<>(foundPathItems);
        }
    }


    public void setOnAddNewItemListener(FoundFileItemListener onAddNewItemListener) {
        this.onAddNewItemListener = onAddNewItemListener;
    }

    public void clearAll() {
        long clearGeneration;
        synchronized (foundPathItems) {
            getFoundItems().clear();
            generation++;
            clearGeneration = generation;
        }

        FoundFileItemListener listener = onAddNewItemListener;
        if (listener != null) listener.onClearAll(clearGeneration);
    }

    public long getGeneration() {
        synchronized (foundPathItems) {
            return generation;
        }
    }
}
