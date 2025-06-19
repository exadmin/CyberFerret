package com.github.exadmin.cyberferret.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FoundItemsContainer {
    private final List<FoundPathItem> foundPathItems = Collections.synchronizedList(new ArrayList<>());
    private FoundFileItemListener onAddNewItemListener;

    public void addItem(FoundPathItem newItem) {
        foundPathItems.add(newItem);
        onAddNewItemListener.newItemAdded(newItem);
    }

    // do not public this api
    private List<FoundPathItem> getFoundItems() {
        return foundPathItems;
    }

    public int getFoundItemsSize() {
        return foundPathItems.size();
    }

    public List<FoundPathItem> getFoundItemsCopy() {
        return new ArrayList<>(foundPathItems);
    }


    public void setOnAddNewItemListener(FoundFileItemListener onAddNewItemListener) {
        this.onAddNewItemListener = onAddNewItemListener;
    }

    public void clearAll() {
        getFoundItems().clear();
        onAddNewItemListener.onClearAll();
    }
}
