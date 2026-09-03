package com.github.exadmin.cyberferret;

import javafx.application.Application;

/**
 * Starts the JavaFX application without exposing an {@link Application} subclass as the executable JAR entry point.
 */
public final class Launcher {
    /**
     * Prevents construction because this class only provides the executable entry point.
     */
    private Launcher() {
    }

    /**
     * Starts Cyber Ferret and passes command-line arguments to JavaFX.
     *
     * <p>JavaFX permits one application launch per JVM. This method blocks until the application exits and may throw
     * an unchecked exception when the JavaFX runtime cannot start.</p>
     *
     * @param args command-line arguments passed to the JavaFX application
     */
    public static void main(String[] args) {
        Application.launch(CyberFerretApp.class, args);
    }
}
