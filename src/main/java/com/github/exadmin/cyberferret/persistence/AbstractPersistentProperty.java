package com.github.exadmin.cyberferret.persistence;

import javafx.beans.property.Property;

import java.util.Map;

public abstract class AbstractPersistentProperty<T> {
    private final String keyName;
    protected final T defaultValue;

    AbstractPersistentProperty(String keyName, T defaultValue , Map<String, AbstractPersistentProperty<?>> regMap) {
        this.keyName = keyName;
        this.defaultValue = defaultValue;

        if (regMap.containsKey(keyName)) throw new IllegalArgumentException("Duplicate key");
        regMap.put(keyName, this);
    }

    @Override
    public String toString() {
        return keyName;
    }

    public T getValue() {
        return getFxProperty().getValue();
    }

    public void setValue(T newValue) {
        getFxProperty().setValue(newValue);
    }

    public abstract Property<T> getFxProperty();

    public abstract void parseValue(Object strValue);
}
