@echo off
if defined JAVAFX_PATH (
    if not "%JAVAFX_PATH%"=="" (
        echo Environment variable JAVAFX_PATH is set to: %JAVAFX_PATH%
        start javaw --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar cyberferret-1.0.1.jar
        exit /b 0
    ) else (
        goto error
    )
) else (
    goto error
)

:error
echo "ERROR: 'JAVAFX_PATH' variable is not set or has empty value."
echo "Install JDK from https://jdk.java.net/24/ then setup 'JAVAFX_PATH' variable mapping to it"
echo "Example 'JAVAFX_PATH' = 'c:\JDKs\javafx-sdk-24.0.1'"
rundll32 sysdm.cpl,EditEnvironmentVariables
pause
