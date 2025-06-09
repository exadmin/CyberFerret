package com.github.exadmin.sourcesscanner.context;

public abstract class AppAbstractProperty<T> {
    public static final AppAbstractProperty<Double> STAGE_WIDTH = new AppDoubleProperty("stage.width", 640d);
    public static final AppAbstractProperty<Double> STAGE_HEIGTH = new AppDoubleProperty("stage.height", 480d);
    public static final AppAbstractProperty<Double> STAGE_POSX = new AppDoubleProperty("stage.posX", 0d);
    public static final AppAbstractProperty<Double> STAGE_POSY = new AppDoubleProperty("stage.posY", 0d);
    public static final AppAbstractProperty<String> DICTIONARY = new AppStringProperty("dictionary", "");
    public static final AppAbstractProperty<String> DIR_TO_SCAN = new AppStringProperty("dir-to-scan", "");
    public static final AppAbstractProperty<Boolean> STAGE_IS_MAXIMIZED = new AppBooleanProperty("stage.maximized", false);

    private final String keyName;
    private final T defaultValue;

    AppAbstractProperty(String keyName, T defaultValue) {
        this.keyName = keyName;
        this.defaultValue = defaultValue;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        return keyName;
    }

    public abstract T parseValue(String text);
}
