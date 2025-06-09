package com.github.exadmin.sourcesscanner.context;

public class AppDoubleProperty extends AppAbstractProperty<Double> {

    public AppDoubleProperty(String keyName, Double defaultValue) {
        super(keyName, defaultValue);
    }

    @Override
    public Double parseValue(String text) {
        return Double.parseDouble(text);
    }
}
