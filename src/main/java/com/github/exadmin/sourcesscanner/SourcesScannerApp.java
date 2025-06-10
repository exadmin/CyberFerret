package com.github.exadmin.sourcesscanner;

import com.github.exadmin.sourcesscanner.fxui.SceneBuilder;
import com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Paths;

import static com.github.exadmin.sourcesscanner.persistence.PersistentPropertiesManager.*;

public class SourcesScannerApp extends Application {
    private static final String APPLICATION_PERSISTENT_CONTEXT_FILENAME = "app.properties";

    @Override
    public void start(Stage stage) {
        PersistentPropertiesManager appProperties = new PersistentPropertiesManager(Paths.get("", APPLICATION_PERSISTENT_CONTEXT_FILENAME));

        // add listeners
        stage.widthProperty().addListener((value, oldValue, newValue) -> STAGE_WIDTH.parseValue(newValue));
        stage.heightProperty().addListener((value, oldValue, newValue) -> STAGE_HEIGHT.parseValue(newValue));
        stage.xProperty().addListener((value, oldValue, newValue) -> STAGE_POSX.parseValue(newValue));
        stage.yProperty().addListener((value, oldValue, newValue) -> STAGE_POSY.parseValue(newValue));
        stage.maximizedProperty().addListener((value, oldValue, newValue) -> STAGE_IS_MAXIMIZED.parseValue(newValue));

        Boolean isStageMaximized = STAGE_IS_MAXIMIZED.getValue();
        if (isStageMaximized) stage.setMaximized(true);

        stage.setWidth(STAGE_WIDTH.getValue());
        stage.setHeight(STAGE_HEIGHT.getValue());
        stage.setX(STAGE_POSX.getValue());
        stage.setY(STAGE_POSY.getValue());

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