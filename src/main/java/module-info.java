module com.casarural.sistemapdv {
    requires javafx.controls;
    requires javafx.fxml;

    requires java.sql;
    requires com.zaxxer.hikari;

    opens com.casarural.sistemapdv.model.entities to javafx.base, javafx.fxml;
    opens com.casarural.sistemapdv.controller to javafx.fxml;
    opens com.casarural.sistemapdv to javafx.fxml;
    exports com.casarural.sistemapdv;
}