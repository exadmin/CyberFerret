package com.github.exadmin.cyberferret.model;

public interface FoundFileItemListener {
    void newItemAdded(FoundPathItem newItem);
    void onClearAll();
}
