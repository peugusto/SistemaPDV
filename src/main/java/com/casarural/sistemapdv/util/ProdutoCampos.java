package com.casarural.sistemapdv.util;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Liga as opções "Pesável" e "Controlar estoque" das telas de cadastro e edição de produto,
 * para as duas se comportarem igual:
 * - pesável não controla estoque (a caixa fica desmarcada e travada) e o preço vira "do kg";
 * - sem controle de estoque, o campo de quantidade fica travado.
 */
public final class ProdutoCampos {

    private ProdutoCampos() {}

    public static void ligar(CheckBox chkPesavel, CheckBox chkControlaEstoque, TextField txtEstoque, Label lblPreco) {
        chkPesavel.selectedProperty().addListener((obs, antes, pesavel) -> atualizar(chkPesavel, chkControlaEstoque, txtEstoque, lblPreco));
        chkControlaEstoque.selectedProperty().addListener((obs, antes, controla) -> atualizar(chkPesavel, chkControlaEstoque, txtEstoque, lblPreco));
        atualizar(chkPesavel, chkControlaEstoque, txtEstoque, lblPreco);
    }

    private static void atualizar(CheckBox chkPesavel, CheckBox chkControlaEstoque, TextField txtEstoque, Label lblPreco) {
        boolean pesavel = chkPesavel.isSelected();
        if (pesavel) chkControlaEstoque.setSelected(false);
        chkControlaEstoque.setDisable(pesavel || chkPesavel.isDisable());
        lblPreco.setText(pesavel ? "Preço do kg (R$)" : "Preço (R$)");

        boolean controla = chkControlaEstoque.isSelected();
        txtEstoque.setDisable(!controla);
        if (!controla && txtEstoque.getText().isBlank()) txtEstoque.setText("0");
    }

    /** Quantidade digitada; vazio conta como 0. */
    public static int estoque(TextField txtEstoque) {
        String texto = txtEstoque.getText();
        return texto == null || texto.isBlank() ? 0 : Integer.parseInt(texto.trim());
    }
}
