module com.github.exadmin.sourcesscanner {
    requires javafx.controls;
    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.slf4j;
    requires org.apache.logging.log4j.core;

    exports com.github.exadmin.sourcesscanner;
    opens com.github.exadmin.sourcesscanner.model to javafx.base;
    opens com.github.exadmin.sourcesscanner.fxui.logger to org.apache.logging.log4j.core;
}