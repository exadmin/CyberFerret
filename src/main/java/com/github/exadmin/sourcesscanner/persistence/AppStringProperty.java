package com.github.exadmin.sourcesscanner.persistence;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;

public class AppStringProperty extends AbstractPersistentProperty<String> {
    private final StringProperty fxProperty = new SimpleStringProperty();

    AppStringProperty(String keyName, String defaultValue, Map<String, AbstractPersistentProperty<?>> regMap) {
        super(keyName, defaultValue, regMap);
    }

    @Override
    protected Property<String> getFxProperty() {
        return fxProperty;
    }

    @Override
    public void parseValue(Object text) {
        setValue(text.toString());
    }
}
