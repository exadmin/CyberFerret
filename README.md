# Cyber Ferret
[<img src="./docs/cyber-ferret.jpg">]()

Scans any files for different pre-defined signatures (supporting RegExp and other rules)

# How to build
```shell
mvn clean package assembly:single
```

# How to run - Windows version
## prerequisites
Install JDK from https://jdk.java.net/24/
Install JavaFX from https://gluonhq.com/products/javafx/
```shell
java --module-path "...\JDKs\javafx-sdk-24.0.1\lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar cyberferret.jar
```

# How to run - linux version
## prerequisites
Install JDK from https://jdk.java.net/24/
Install JavaFX from https://gluonhq.com/products/javafx/
```shell
java --module-path $path_to_javafx_sdk/lib --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar ./target/cyber-ferret.jar
```
# How to run - in IntelliJ IDEA
## prerequisites
Install JDK from https://jdk.java.net/24/
Install JavaFX from https://gluonhq.com/products/javafx/
Create Run/Debug Configuration Profile of type "Application"
Set VM options "--module-path "...\JDKs\javafx-sdk-24.0.1\lib" --add-modules  javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics"