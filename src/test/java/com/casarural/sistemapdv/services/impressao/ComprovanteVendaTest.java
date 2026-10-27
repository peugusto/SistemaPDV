package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComprovanteVendaTest {
    static Order venda() {
        Order venda = new Order();
        venda.setIdPedido(123);
        venda.setItemPedido(new ArrayList<>(List.of(
                new OrderItem(new Product("1", "Arroz 5 kg", Money.parse("12,50"), 10), 2, Money.parse("12,50")),
                new OrderItem(new Product("2", "Café torrado 500 g", Money.parse("18"), 10), 1, Money.parse("18")))));
        venda.aplicarDesconto(Money.parse("3"));
        return venda;
    }

    @Test
    void dinheiroComDescontoETrocoUsaValoresDaVenda() {
        Order venda = venda();
        venda.getPagamentos().add(new Order.PaymentSlice(Money.parse("40"), PaymentMethod.DINHEIRO));
        String texto = ComprovanteVenda.gerar(venda, Money.parse("10"), Map.of()).getTexto();
        assertTrue(texto.contains("CASA RURAL"));
        assertTrue(texto.contains("NÃO É DOCUMENTO FISCAL"));
        assertTrue(texto.contains("Café torrado 500 g"));
        assertTrue(texto.matches("(?s).*Subtotal +R\\$ 43,00.*Desconto +- R\\$ 3,00.*TOTAL +R\\$ 40,00.*"));
        assertTrue(texto.matches("(?s).*Dinheiro +R\\$ 40,00.*Recebido em dinheiro +R\\$ 50,00.*Troco +R\\$ 10,00.*"));
    }

    @Test
    void pagamentosDivididosEFiadoNaoSaoApresentadosComoQuitacao() {
        Order venda = venda();
        Customer cliente = new Customer();
        cliente.setNomeCliente("João da Silva");
        venda.setCostumer(cliente);
        venda.getPagamentos().add(new Order.PaymentSlice(Money.parse("15"), PaymentMethod.PIX));
        venda.getPagamentos().add(new Order.PaymentSlice(Money.parse("25"), PaymentMethod.FIADO));
        String texto = ComprovanteVenda.gerar(venda, Money.ZERO, Map.of()).getTexto();
        assertTrue(texto.contains("Cliente: João da Silva"));
        assertTrue(texto.matches("(?s).*Fiado \\(a pagar\\) +R\\$ 25,00.*PIX +R\\$ 15,00.*"));
        assertFalse(texto.contains("Recebido em dinheiro"));
    }

    @Test
    void creditoDaTrocaNaoAumentaODinheiroRecebidoHoje() {
        Order venda = venda();
        venda.setIdPedidoTrocado(100);
        venda.getPagamentos().add(new Order.PaymentSlice(Money.parse("30"), PaymentMethod.DINHEIRO, true));
        venda.getPagamentos().add(new Order.PaymentSlice(Money.parse("10"), PaymentMethod.DINHEIRO));
        String texto = ComprovanteVenda.gerar(venda, Money.parse("10"),
                Map.of(PaymentMethod.PIX, Money.parse("5"))).getTexto();
        assertTrue(texto.contains("Troca da venda: #100"));
        assertTrue(texto.matches("(?s).*Crédito troca - Dinheiro +R\\$ 30,00.*Recebido em dinheiro +R\\$ 20,00.*"));
        assertTrue(texto.matches("(?s).*DEVOLVER AO CLIENTE PELA TROCA.*PIX +R\\$ 5,00.*"));
    }

    @Test
    void nomesLongosEAcentosRespeitamLarguraESemControles() {
        Order venda = venda();
        venda.getItemPedido().get(0).getProduto().setNomeProduto("Café\n\u001b\u000c especial " + "produto".repeat(20));
        String texto = ComprovanteVenda.gerar(venda, Money.ZERO, Map.of()).getTexto();
        assertTrue(texto.contains("Café"));
        assertFalse(texto.contains("\u001b"));
        assertFalse(texto.contains("\u000c"));
        texto.lines().forEach(linha -> assertTrue(linha.length() <= ComprovanteVenda.COLUNAS, linha));
        assertTrue(texto.contains("produto"));
    }

    @Test
    void vendaSemIdSalvoNaoGeraComprovante() {
        Order venda = venda();
        venda.setIdPedido(0);
        assertThrows(IllegalArgumentException.class,
                () -> ComprovanteVenda.gerar(venda, Money.ZERO, Map.of()));
    }
}
