package com.github.exadmin.cyberferret.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FoundItemsContainerTests {

    @Test
    public void clearAllIncrementsGenerationAndNotifiesListenerWithNewGeneration() {
        FoundItemsContainer container = new FoundItemsContainer();
        AtomicLong notifiedGeneration = new AtomicLong(-1);
        container.setOnAddNewItemListener(new FoundFileItemListener() {
            @Override
            public void newItemAdded(FoundPathItem newItem) {
            }

            @Override
            public void onClearAll() {
            }

            @Override
            public void onClearAll(long generation) {
                notifiedGeneration.set(generation);
            }
        });

        container.clearAll();

        assertEquals(1, container.getGeneration());
        assertEquals(1, notifiedGeneration.get());
    }

    @Test
    public void addItemNotifiesListenerWithCurrentGeneration() {
        FoundItemsContainer container = new FoundItemsContainer();
        container.clearAll();
        AtomicLong notifiedGeneration = new AtomicLong(-1);
        container.setOnAddNewItemListener(new FoundFileItemListener() {
            @Override
            public void newItemAdded(FoundPathItem newItem) {
            }

            @Override
            public void newItemAdded(FoundPathItem newItem, long generation) {
                notifiedGeneration.set(generation);
            }

            @Override
            public void onClearAll() {
            }
        });

        container.addItem(new FoundPathItem(Path.of("file.txt"), ItemType.FILE, null));

        assertEquals(1, notifiedGeneration.get());
        assertEquals(1, container.getFoundItemsCopy().size());
    }

    @Test
    public void getFoundItemsCopyReturnsSnapshot() {
        FoundItemsContainer container = new FoundItemsContainer();
        container.addItem(new FoundPathItem(Path.of("file.txt"), ItemType.FILE, null));

        var snapshot = container.getFoundItemsCopy();
        container.clearAll();

        assertEquals(1, snapshot.size());
        assertTrue(container.getFoundItemsCopy().isEmpty());
    }

    @Test
    public void notifyItemUpdatedUsesCurrentGeneration() {
        FoundItemsContainer container = new FoundItemsContainer();
        container.clearAll();
        FoundPathItem item = new FoundPathItem(Path.of("file.txt"), ItemType.FILE, null);
        container.addItem(item);
        AtomicReference<FoundPathItem> notifiedItem = new AtomicReference<>();
        AtomicLong notifiedGeneration = new AtomicLong(-1);
        container.setOnAddNewItemListener(new FoundFileItemListener() {
            @Override
            public void newItemAdded(FoundPathItem newItem) {
            }

            @Override
            public void itemUpdated(FoundPathItem updatedItem, long generation) {
                notifiedItem.set(updatedItem);
                notifiedGeneration.set(generation);
            }

            @Override
            public void onClearAll() {
            }
        });

        item.setIgnored(true);
        container.notifyItemUpdated(item);

        assertEquals(item, notifiedItem.get());
        assertEquals(1, notifiedGeneration.get());
    }
}
