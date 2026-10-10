module com.casarural.sistemapdv {
    requires javafx.controls;
    requires javafx.fxml;

    requires java.sql;
    requires com.zaxxer.hikari;
    // Flyway 10 não declara nome de módulo; o Java deriva "flyway.core" do nome do jar
    requires flyway.core;
    // Só a interface de log; o Logback é encontrado sozinho na execução (ServiceLoader)
    requires org.slf4j;

    opens com.casarural.sistemapdv.model.entities to javafx.base, javafx.fxml;
    opens com.casarural.sistemapdv.controller to javafx.fxml;
    opens com.casarural.sistemapdv to javafx.fxml;
    exports com.casarural.sistemapdv;
}