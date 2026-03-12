package com.github.exadmin.cyberferret.persistence;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;

import java.util.Map;

public class AppDoubleProperty extends AbstractPersistentProperty<Number> {
    private final DoubleProperty fxProperty = new SimpleDoubleProperty();

    AppDoubleProperty(String keyName, Number defaultValue, Map<String, AbstractPersistentProperty<?>> regMap) {
        super(keyName, defaultValue, regMap);
        setValue(defaultValue);
    }

    @Override
    public void parseValue(Object text) {
        setValue(Double.parseDouble(text.toString()));
    }

    @Override
    public Property<Number> getFxProperty() {
        return fxProperty;
    }
}
