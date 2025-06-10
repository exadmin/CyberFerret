package com.github.exadmin.sourcesscanner.persistence;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;

import java.util.Map;

public class AppDoubleProperty extends AbstractPersistentProperty<Double> {
    private final DoubleProperty fxProperty = new SimpleDoubleProperty();

    AppDoubleProperty(String keyName, Double defaultValue, Map<String, AbstractPersistentProperty<?>> regMap) {
        super(keyName, defaultValue, regMap);
    }

    @Override
    public void parseValue(Object text) {
        setValue(Double.parseDouble(text.toString()));
    }

    @Override
    protected Property getFxProperty() {
        return fxProperty;
    }
}
