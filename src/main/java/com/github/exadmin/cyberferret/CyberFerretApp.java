package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.fxui.SceneBuilder;
import com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Properties;

import static com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager.*;

public class CyberFerretApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(CyberFerretApp.class);
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


        stage.setMaximized(STAGE_IS_MAXIMIZED.getValue());
        stage.setWidth(STAGE_WIDTH.getValue().doubleValue());
        stage.setHeight(STAGE_HEIGHT.getValue().doubleValue());
        stage.setX(STAGE_POSX.getValue().doubleValue());
        stage.setY(STAGE_POSY.getValue().doubleValue());

        stage.setOnCloseRequest(windowEvent -> appProperties.saveProperties());

        SceneBuilder sceneBuilder = new SceneBuilder(stage);
        Scene scene = sceneBuilder.buildScene();
        stage.setScene(scene);
        stage.show();

        String appVer = loadApplicationVersion();
        stage.setTitle("Cyber Ferret (version " + appVer + ")");
    }

    public static void main(String[] args) {
        launch();
    }

    private String loadApplicationVersion() {
        Properties props = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/version.properties")) {
            props.load(input);
            return props.getProperty("application.version");
        } catch (IOException | NullPointerException ex) {
            log.error("Error while loading {}", "/version.properties", ex);
        }

        return "UNDEFINED";
    }
}