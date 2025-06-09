package com.github.exadmin.sourcesscanner.context;

public class AppBooleanProperty extends AppAbstractProperty<Boolean> {
    public AppBooleanProperty(String keyName, Boolean defaultValue) {
        super(keyName, defaultValue);
    }

    @Override
    public Boolean parseValue(String text) {
        return Boolean.parseBoolean(text);
    }
}
