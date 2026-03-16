module com.casarural.sistemapdv {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.casarural.sistemapdv to javafx.fxml;
    exports com.casarural.sistemapdv;
}