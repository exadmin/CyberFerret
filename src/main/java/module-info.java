module com.github.exadmin.sourcesscanner {
    requires javafx.controls;
    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.slf4j;
    requires org.apache.logging.log4j.core;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;
    requires java.xml; // for log4j configuration plugins

    exports com.github.exadmin.sourcesscanner;
    opens com.github.exadmin.sourcesscanner.model to javafx.base;
    opens com.github.exadmin.sourcesscanner.fxui.logger to org.apache.logging.log4j.core;
    opens com.github.exadmin.sourcesscanner.exclude to com.fasterxml.jackson.databind;
}