package com.github.exadmin.cyberferret.persistence;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.Map;

public class AppBooleanProperty extends AbstractPersistentProperty<Boolean> {
    private final BooleanProperty fxProperty = new SimpleBooleanProperty();

    AppBooleanProperty(String keyName, Boolean defaultValue, Map<String, AbstractPersistentProperty<?>> regMap) {
        super(keyName, defaultValue, regMap);
    }

    @Override
    public Property<Boolean> getFxProperty() {
        return fxProperty;
    }

    @Override
    public void parseValue(Object text) {
        if (text == null) {
            setValue(false);
            return;
        }

        setValue(Boolean.parseBoolean(text.toString()));
    }
}
