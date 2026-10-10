package com.casarural.sistemapdv.util;

import javafx.scene.control.TextField;

public class Constraints {
    public static void setTextFieldInteger(TextField txt) {
        txt.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                txt.setText(oldValue);
            }
        });
    }

    public static void setTextFieldMaxLength(TextField txt, int max) {
        txt.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > max) {
                txt.setText(oldValue);
            }
        });
    }

    /** Campo de dinheiro: só dígitos, um separador decimal (vírgula ou ponto) e até 2 casas. */
    public static void setTextFieldMoney(TextField txt) {
        txt.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*([.,]\\d{0,2})?")) {
                txt.setText(oldValue);
            }
        });
    }
}
