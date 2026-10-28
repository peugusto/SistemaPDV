package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.RecebimentoFiado;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComprovanteFiadoTest {
    static RecebimentoFiado exemplo(boolean quita) {
        LocalDateTime data = LocalDateTime.of(2026, 10, 27, 14, 30);
        List<RecebimentoFiado.Produto> produtos1 = List.of(
                new RecebimentoFiado.Produto("Arroz 5 kg", 2, Money.parse("12,50")),
                new RecebimentoFiado.Produto("Café torrado", 1, Money.parse("30")));
        List<RecebimentoFiado.Produto> produtos2 = List.of(
                new RecebimentoFiado.Produto("Feijão 1 kg", 2, Money.parse("20")));
        return new RecebimentoFiado(900, 9, "Maria da Silva", "11987654321", data, PaymentMethod.PIX,
                Money.parse("70"), Money.parse(quita ? "70" : "35"), List.of(
                new RecebimentoFiado.Compra(101, data.minusDays(10), Money.parse("50"), Money.parse("5"),
                        Money.parse("30"), Money.parse("30"), produtos1),
                new RecebimentoFiado.Compra(102, data.minusDays(5), Money.parse("40"), Money.ZERO,
                        Money.parse("40"), Money.parse(quita ? "40" : "5"), produtos2)));
    }

    @Test
    void quitacaoMostraClienteETodosOsProdutosDasComprasQuitadas() {
        Comprovante comprovante = ComprovanteFiado.gerar(exemplo(true));
        String texto = comprovante.getTexto();
        assertTrue(texto.contains("PAGAMENTO DE FIADO"));
        assertTrue(texto.contains("Cliente: Maria da Silva"));
        assertTrue(texto.contains("Telefone: (11) 98765-4321"));
        for (String produto : List.of("Arroz 5 kg", "Café torrado", "Feijão 1 kg")) assertTrue(texto.contains(produto));
        assertTrue(texto.contains("------------ 17/10/2026 ------------"));
        assertTrue(texto.contains("------------ 22/10/2026 ------------"));
        assertTrue(texto.matches("(?s).*TOTAL DO FIADO +R\\$ 70,00.*PAGO AGORA +R\\$ 70,00.*RESTANTE +R\\$ 0,00.*"));
        assertTrue(texto.matches("(?s).*2 x Arroz 5 kg +R\\$ 25,00.*1 x Café torrado +R\\$ 30,00.*"));
        assertEquals("Casa Rural - Recebimento de fiado #900", comprovante.getNomeTrabalho());
        texto.lines().forEach(linha -> assertTrue(linha.length() <= 36, linha));
    }

    @Test
    void parcialMostraProdutosESaldoCorretoComUmUnicoResumoNoFinal() {
        String texto = ComprovanteFiado.gerar(exemplo(false)).getTexto();
        assertTrue(texto.matches("(?s).*Feijão 1 kg.*TOTAL DO FIADO +R\\$ 70,00.*PAGO AGORA +R\\$ 35,00.*RESTANTE +R\\$ 35,00.*"));
        assertEquals(1, texto.lines().filter(linha -> linha.startsWith("TOTAL DO FIADO")).count());
        assertEquals(1, texto.lines().filter(linha -> linha.startsWith("PAGO AGORA")).count());
        assertEquals(1, texto.lines().filter(linha -> linha.startsWith("RESTANTE")).count());
        for (String repetido : List.of("Compra #", "QUITADA NESTE PAGAMENTO", "COMPRA PARCIALMENTE PAGA",
                "Pago anteriormente", "Pago agora nesta compra", "Falta nesta compra", "Desconto da compra")) {
            assertFalse(texto.contains(repetido), repetido);
        }
        texto.lines().forEach(linha -> assertTrue(linha.length() <= 36, linha));
    }

    @Test
    void listasNaoPodemMudarDepoisDaConfirmacaoDoRecebimento() {
        RecebimentoFiado original = exemplo(false);
        List<RecebimentoFiado.Produto> produtos = new ArrayList<>(original.getCompras().get(0).getProdutos());
        RecebimentoFiado.Compra compra = new RecebimentoFiado.Compra(101, original.getData(), Money.parse("50"),
                Money.parse("5"), Money.parse("30"), Money.parse("30"), produtos);
        produtos.clear();
        assertEquals(2, compra.getProdutos().size());
        assertThrows(UnsupportedOperationException.class, () -> original.getCompras().clear());
        assertThrows(UnsupportedOperationException.class, () -> compra.getProdutos().clear());
    }

    @Test
    void dadosFinanceirosInconsistentesNaoGeramQuitacaoFalsa() {
        RecebimentoFiado rec = exemplo(false);
        assertThrows(IllegalArgumentException.class, () -> new RecebimentoFiado(900, 9, "Maria", null,
                rec.getData(), PaymentMethod.PIX, Money.parse("70"), Money.parse("70"), rec.getCompras()));
        assertThrows(IllegalArgumentException.class, () -> new RecebimentoFiado.Compra(101, rec.getData(),
                Money.parse("50"), Money.ZERO, Money.parse("30"), Money.parse("31"), List.of()));
    }

    @Test
    void mesmoDestinoDePreviaRecebeFiadoSemProcurarImpressora() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PreviaTerminal terminal = new PreviaTerminal(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        ImpressaoVendaService impressao = new ImpressaoVendaService(terminal::enviar, Runnable::run);
        impressao.emitir(exemplo(false)).join();
        String texto = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("Maria da Silva"));
        assertTrue(texto.contains("Feijão 1 kg"));
        assertTrue(texto.contains("nenhum trabalho enviado à impressora"));
    }
}
