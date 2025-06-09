package com.github.exadmin.sourcesscanner;

import com.github.exadmin.sourcesscanner.context.AppAbstractProperty;
import com.github.exadmin.sourcesscanner.context.AppProperties;
import com.github.exadmin.sourcesscanner.fxui.SceneBuilder;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Paths;

import static com.github.exadmin.sourcesscanner.context.AppAbstractProperty.*;

public class SourcesScannerApp extends Application {
    private static final String APPLICATION_PERSISTENT_CONTEXT_FILENAME = "app.properties";

    @Override
    public void start(Stage stage) throws IOException {
        AppProperties appProperties = new AppProperties(Paths.get("", APPLICATION_PERSISTENT_CONTEXT_FILENAME));

        // add listeners
        stage.widthProperty().addListener((value, oldValue, newValue) -> appProperties.setValue(STAGE_WIDTH, newValue.doubleValue()));
        stage.heightProperty().addListener((value, oldValue, newValue) -> appProperties.setValue(STAGE_HEIGTH, newValue.doubleValue()));
        stage.xProperty().addListener((value, oldValue, newValue) -> appProperties.setValue(STAGE_POSX, newValue.doubleValue()));
        stage.yProperty().addListener((value, oldValue, newValue) -> appProperties.setValue(STAGE_POSY, newValue.doubleValue()));
        stage.maximizedProperty().addListener((value, oldValue, newValue) -> appProperties.setValue(STAGE_IS_MAXIMIZED, newValue));

        Boolean isStageMaximized = appProperties.getValue(STAGE_IS_MAXIMIZED);
        if (isStageMaximized) stage.setMaximized(true);

        stage.setWidth(appProperties.getValue(STAGE_WIDTH));
        stage.setHeight(appProperties.getValue(STAGE_HEIGTH));
        stage.setX(appProperties.getValue(STAGE_POSX));
        stage.setY(appProperties.getValue(STAGE_POSY));

        stage.setOnCloseRequest(windowEvent -> appProperties.saveProperties());

        SceneBuilder sceneBuilder = new SceneBuilder(appProperties, stage);
        Scene scene = sceneBuilder.buildScene();
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}