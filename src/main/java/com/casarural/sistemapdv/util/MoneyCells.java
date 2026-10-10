package com.casarural.sistemapdv.util;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.math.BigDecimal;

/** Exibe colunas de tabela com valores em dinheiro no formato "R$ 1.234,56". */
public final class MoneyCells {

    private MoneyCells() {}

    public static <T> void format(TableColumn<T, BigDecimal> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Money.format(item));
            }
        });
    }
}
