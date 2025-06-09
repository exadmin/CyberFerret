package com.github.exadmin.sourcesscanner.context;

public class AppStringProperty extends AppAbstractProperty<String> {
    public AppStringProperty(String keyName, String defaultValue) {
        super(keyName, defaultValue);
    }

    @Override
    public String parseValue(String text) {
        return text;
    }
}
