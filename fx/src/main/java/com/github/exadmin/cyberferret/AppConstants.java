package com.github.exadmin.cyberferret;

public final class AppConstants {
    public static final String SYS_ENV_VAR_PASSWORD = "CYBER_FERRET_PASSWORD";

    /** Default width, in pixels, for buttons created by the FX UI. */
    public static final int DEFAULT_BUTTON_WIDTH = 120;

    /** Default width, in pixels, for labels created by the FX UI. */
    public static final int DEFAULT_LABEL_WIDTH = 120;

    /** Maximum time, in seconds, allowed for a cfcli exclusion process to finish. */
    public static final int CFCLI_EXCLUSION_TIMEOUT_SECONDS = 60;

    /** Maximum number of bytes read from a file to build signature context. */
    public static final int MAX_CONTEXT_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private AppConstants() {
    }
}
