package com.github.exadmin.cyberferret;

public final class AppConstants {
    public static final String SYS_ENV_VAR_PASSWORD = "CYBER_FERRET_PASSWORD";
    public static final String CYBER_FERRET_ONLINE_DICTIONARY_URL =
            "https://raw.githubusercontent.com/exadmin/CyberFerretDictionary/main/dictionary-latest.encrypted";
    public static final String DICTIONARY_FILE_PATH_ENCRYPTED = "./dictionary-latest-cache.encrypted";
    public static final String DICTIONARY_FILE_PATH_DECRYPTED = "./dictionary-latest-cache.decrypted";

    private AppConstants() {
    }
}
