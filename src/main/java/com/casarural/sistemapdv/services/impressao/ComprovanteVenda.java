package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cópia imutável do comprovante: a impressão não acessa o carrinho nem o banco. */
public final class ComprovanteVenda {
    public static final int COLUNAS = 36;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final int idVenda;
    private final String texto;

    private ComprovanteVenda(int idVenda, String texto) {
        this.idVenda = idVenda;
        this.texto = texto;
    }

    public int getIdVenda() { return idVenda; }
    public String getTexto() { return texto; }

    public static ComprovanteVenda gerar(Order venda, BigDecimal troco,
                                         Map<PaymentMethod, BigDecimal> devolucoes) {
        if (venda.getIdPedido() <= 0) {
            throw new IllegalArgumentException("O comprovante só pode ser emitido depois de salvar a venda.");
        }
        List<String> linhas = new ArrayList<>();
        centralizar(linhas, "CASA RURAL");
        centralizar(linhas, "COMPROVANTE DE VENDA");
        centralizar(linhas, "NÃO É DOCUMENTO FISCAL");
        separar(linhas);
        escrever(linhas, "Venda: #" + venda.getIdPedido());
        escrever(linhas, "Emitido em: " + DATA.format(LocalDateTime.now()));
        if (venda.getCostumer() != null) {
            escrever(linhas, "Cliente: " + venda.getCostumer().getNomeCliente());
        }
        if (venda.isTroca()) escrever(linhas, "Troca da venda: #" + venda.getIdPedidoTrocado());
        separar(linhas);
        int numero = 1;
        for (OrderItem item : venda.getItemPedido()) {
            escrever(linhas, String.format("%02d %s", numero++, item.getNomeProduto()));
            valores(linhas, "   " + item.getQtd() + " x " + Money.format(item.getPrecoUnitario()),
                    Money.format(item.getSubtotal()));
        }
        separar(linhas);
        valores(linhas, "Subtotal", Money.format(venda.getSubtotalItens()));
        if (Money.isPositive(venda.getDesconto())) {
            valores(linhas, "Desconto", "- " + Money.format(venda.getDesconto()));
        }
        valores(linhas, "TOTAL", Money.format(venda.getValorTotal()));
        separar(linhas);
        escrever(linhas, "FORMAS DE PAGAMENTO");
        BigDecimal dinheiroNovo = Money.ZERO;
        for (boolean credito : new boolean[]{false, true}) {
            for (PaymentMethod metodo : PaymentMethod.values()) {
                BigDecimal valor = venda.getPagamentos().stream()
                        .filter(p -> p.isCredito() == credito && p.getMetodo() == metodo)
                        .map(Order.PaymentSlice::getValor).reduce(Money.ZERO, BigDecimal::add);
                if (!Money.isPositive(valor)) continue;
                String rotulo = credito ? "Crédito troca - " + nome(metodo)
                        : metodo == PaymentMethod.FIADO ? "Fiado (a pagar)" : nome(metodo);
                valores(linhas, rotulo, Money.format(valor));
                if (!credito && metodo == PaymentMethod.DINHEIRO) dinheiroNovo = valor;
            }
        }
        if (Money.isPositive(dinheiroNovo)) {
            valores(linhas, "Recebido em dinheiro", Money.format(dinheiroNovo.add(Money.of(troco))));
            valores(linhas, "Troco", Money.format(troco));
        }
        if (!devolucoes.isEmpty()) {
            separar(linhas);
            escrever(linhas, "DEVOLVER AO CLIENTE PELA TROCA");
            for (PaymentMethod metodo : PaymentMethod.values()) {
                BigDecimal valor = devolucoes.get(metodo);
                if (Money.isPositive(valor)) valores(linhas, nome(metodo), Money.format(valor));
            }
        }
        separar(linhas);
        centralizar(linhas, "Obrigado pela preferência!");
        return new ComprovanteVenda(venda.getIdPedido(), String.join("\n", linhas) + "\n");
    }

    private static String nome(PaymentMethod metodo) {
        switch (metodo) {
            case DINHEIRO: return "Dinheiro";
            case CARTAO: return "Cartão";
            case PIX: return "PIX";
            case FIADO: return "Fiado";
            default: throw new IllegalArgumentException("Forma de pagamento desconhecida.");
        }
    }

    private static void separar(List<String> linhas) { linhas.add("-".repeat(COLUNAS)); }

    private static String limpar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").trim();
    }

    private static void escrever(List<String> linhas, String texto) {
        String resto = limpar(texto);
        while (resto.length() > COLUNAS) {
            int corte = resto.lastIndexOf(' ', COLUNAS);
            if (corte <= 0) corte = COLUNAS;
            linhas.add(resto.substring(0, corte));
            resto = resto.substring(corte).trim();
        }
        linhas.add(resto);
    }

    private static void centralizar(List<String> linhas, String texto) {
        String limpo = limpar(texto);
        linhas.add(" ".repeat(Math.max(0, (COLUNAS - limpo.length()) / 2)) + limpo);
    }

    private static void valores(List<String> linhas, String rotulo, String valor) {
        if (rotulo.length() + valor.length() >= COLUNAS) {
            escrever(linhas, rotulo);
            linhas.add(" ".repeat(Math.max(0, COLUNAS - valor.length())) + valor);
        } else {
            linhas.add(rotulo + " ".repeat(COLUNAS - rotulo.length() - valor.length()) + valor);
        }
    }
}
