package com.github.exadmin.sourcesscanner.model;

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

    public List<FoundPathItem> getFoundItems() {
        return foundPathItems;
    }


    public void setOnAddNewItemListener(FoundFileItemListener onAddNewItemListener) {
        this.onAddNewItemListener = onAddNewItemListener;
    }
}
