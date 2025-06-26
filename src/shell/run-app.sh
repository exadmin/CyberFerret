#!/bin/bash

if [[ -n "$JAVAFX_PATH" && "$JAVAFX_PATH" != "" ]]; then
    echo "Environment variable JAVAFX_PATH is set to: $JAVAFX_PATH"
    java --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar cyberferret-1.0.1.jar
    exit 0
else
    echo "ERROR: 'JAVAFX_PATH' variable is not set or has empty value."
    echo "Install JDK from https://jdk.java.net/24/ then setup 'JAVAFX_PATH' variable mapping to it"
    echo "Example 'JAVAFX_PATH' = '/opt/javafx-sdk-24.0.1'"
    echo "You can set the variable by adding this line to your ~/.bashrc or ~/.bash_profile:"
    echo "export JAVAFX_PATH=\"/path/to/javafx-sdk\""
    read -p "Press enter to continue..."
    exit 1
fi