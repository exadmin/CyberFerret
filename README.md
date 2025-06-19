# sources-scanner

Scans any files for different pre-defined signatures (supporting RegExp and other rules)

# How to build
```shell
mvn clean package assembly:single
```

# How to run - windows version
## prerequisites
Install Java from https://jdk.java.net/24/
Install JavaFX from https://gluonhq.com/products/javafx/
```shell
java --module-path "...\JDKs\javafx-sdk-24.0.1\lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar ./target/sig-scanner.jar
```

# How to ru - linux version
## prerequisites
Install Java from https://jdk.java.net/24/
Install JavaFX from https://gluonhq.com/products/javafx/
```shell
java --module-path $path_to_javafx_sdk/lib --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar ./target/sig-scanner.jar
```
