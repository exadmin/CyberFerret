package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.fxui.SceneBuilder;
import com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Paths;

import static com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager.*;

public class CyberFerretApp extends Application {
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

        stage.setWidth(STAGE_WIDTH.getValue().doubleValue());
        stage.setHeight(STAGE_HEIGHT.getValue().doubleValue());
        stage.setX(STAGE_POSX.getValue().doubleValue());
        stage.setY(STAGE_POSY.getValue().doubleValue());

        stage.setOnCloseRequest(windowEvent -> appProperties.saveProperties());

        SceneBuilder sceneBuilder = new SceneBuilder(stage);
        Scene scene = sceneBuilder.buildScene();
        stage.setScene(scene);
        stage.show();

        stage.setTitle("Attention Signatures Scanner, version 1.0.1");
    }

    public static void main(String[] args) {
        launch();
    }
}