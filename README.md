# sources-scanner

Scans any files for different pre-defined signatures (supporting RegExp and other rules)

# How to build
```shell
mvn clean package assembly:single
```

# How to run
```shell
java --module-path "...\JDKs\javafx-sdk-24.0.1\lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar sig-scanner.jar
```