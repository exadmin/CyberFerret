package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.fxui.SceneBuilder;
import com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.github.exadmin.cyberferret.persistence.PersistentPropertiesManager.*;
import static com.github.exadmin.cyberferret.utils.MiscUtils.loadApplicationVersion;

public class CyberFerretApp extends Application {
    private static final String APPLICATION_SETTINGS_DIRECTORY = ".qubership";
    private static final String APPLICATION_PERSISTENT_CONTEXT_FILENAME = "cyberferret.properties";


    @Override
    public void start(Stage stage) {
        Path userHome = Path.of(System.getProperty("user.home"));
        PersistentPropertiesManager appProperties =
                new PersistentPropertiesManager(applicationPropertiesPath(userHome));

        // add listeners
        stage.widthProperty().addListener(normalStageGeometryListener(stage::isMaximized, STAGE_WIDTH::parseValue));
        stage.heightProperty().addListener(normalStageGeometryListener(stage::isMaximized, STAGE_HEIGHT::parseValue));
        stage.xProperty().addListener(normalStageGeometryListener(stage::isMaximized, STAGE_POSX::parseValue));
        stage.yProperty().addListener(normalStageGeometryListener(stage::isMaximized, STAGE_POSY::parseValue));
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

    static Path applicationPropertiesPath(Path userHome) {
        Path settingsDirectory = userHome.resolve(APPLICATION_SETTINGS_DIRECTORY);
        try {
            Files.createDirectories(settingsDirectory);
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "Cannot create application settings directory '" + settingsDirectory.toAbsolutePath() + "'", ex);
        }
        return settingsDirectory.resolve(APPLICATION_PERSISTENT_CONTEXT_FILENAME);
    }

    /**
     * Creates a listener that persists stage geometry changes only while the stage is not maximized.
     *
     * @param maximized supplies the current maximized state
     * @param geometryUpdater persists the new geometry value
     * @return a listener that ignores geometry changes while the stage is maximized
     */
    static ChangeListener<Number> normalStageGeometryListener(
            BooleanSupplier maximized, Consumer<Number> geometryUpdater) {
        return (value, oldValue, newValue) -> {
            if (!maximized.getAsBoolean()) {
                geometryUpdater.accept(newValue);
            }
        };
    }

    public static void main(String[] args) {
        launch();
    }


}
