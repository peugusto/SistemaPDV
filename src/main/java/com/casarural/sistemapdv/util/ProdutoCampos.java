package com.casarural.sistemapdv.util;

import com.casarural.sistemapdv.model.entities.EntradaEstoque;
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

    /**
     * Liga também o custo: no pesável vira "Custo do kg", e ao lado aparece a margem
     * ("Margem: 28%") ou um aviso se o custo passar do preço.
     */
    public static void ligarCusto(CheckBox chkPesavel, TextField txtPreco, TextField txtCusto, Label lblCusto, Label lblMargem) {
        Constraints.setTextFieldMoney(txtCusto);
        Runnable atualizar = () -> {
            lblCusto.setText(chkPesavel.isSelected() ? "Custo do kg (R$)" : "Custo (R$)");
            java.math.BigDecimal preco = lerValor(txtPreco);
            java.math.BigDecimal custo = lerValor(txtCusto);
            if (preco == null || custo == null) {
                lblMargem.setText(custo == null ? "opcional: serve para ver o lucro" : "");
                Tema.estado(lblMargem, null);
            } else if (custo.compareTo(preco) > 0) {
                lblMargem.setText("Custo maior que o preço: prejuízo de " + Money.format(custo.subtract(preco)));
                Tema.estado(lblMargem, "texto-perigo");
            } else {
                lblMargem.setText("Margem: " + com.casarural.sistemapdv.model.entities.RegraLucro.formatarMargem(
                        com.casarural.sistemapdv.model.entities.RegraLucro.margem(preco, custo))
                        + " (lucro de " + Money.format(preco.subtract(custo)) + ")");
                Tema.estado(lblMargem, null);
            }
        };
        chkPesavel.selectedProperty().addListener((o, a, b) -> atualizar.run());
        txtPreco.textProperty().addListener((o, a, b) -> atualizar.run());
        txtCusto.textProperty().addListener((o, a, b) -> atualizar.run());
        atualizar.run();
    }

    /** Valor em R$ digitado; vazio ou inválido = null. */
    public static java.math.BigDecimal lerValor(TextField campo) {
        String t = campo.getText();
        if (t == null || t.isBlank()) return null;
        try {
            return Money.parse(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Custo opcional para gravar: só o campo vazio remove o custo; texto inválido interrompe o salvamento. */
    public static java.math.BigDecimal lerValorParaSalvar(TextField campo) {
        String texto = campo.getText();
        if (texto == null || texto.isBlank()) return null;
        texto = texto.trim();
        if (!texto.matches("(?:\\d+(?:[.,]\\d{0,2})?|[.,]\\d{1,2})")) {
            throw new IllegalArgumentException("Custo inválido. Digite um valor como 12,50 ou deixe vazio para remover o custo.");
        }
        // Valida antes do setter de Product, que arredonda valores com mais de duas casas.
        return EntradaEstoque.validarCusto(new java.math.BigDecimal(texto.replace(',', '.')));
    }

    /** Quantidade digitada; vazio conta como 0. */
    public static int estoque(TextField txtEstoque) {
        String texto = txtEstoque.getText();
        try {
            int quantidade = texto == null || texto.isBlank() ? 0 : Integer.parseInt(texto.trim());
            if (quantidade < 0) throw new NumberFormatException();
            return quantidade;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Digite um estoque inteiro entre 0 e 2.147.483.647.", e);
        }
    }
}
