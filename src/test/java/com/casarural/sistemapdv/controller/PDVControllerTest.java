package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.EntradaEstoque;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Money;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** FXML e controlador reais, serviço real e DAO em memória; não grava vendas nem abre banco. */
@ExtendWith(JavafxTestSupport.class)
class PDVControllerTest {
    private static class Produtos implements ProductDao {
        final Product produto = new Product("900001", "Arroz", Money.parse("2,50"), 20);
        int consultasCodigo;
        int consultasLista;

        Produtos() { produto.setIdProduto(7); }

        public Optional<Product> findByCodBarras(String codigo) {
            consultasCodigo++;
            return produto.getCodBarras().equals(codigo) ? Optional.of(produto) : Optional.empty();
        }
        public List<Product> findAll() { consultasLista++; return List.of(produto); }
        public Optional<Product> findById(Integer id) { throw new AssertionError("Consulta por ID inesperada."); }
        public void insert(Product produto) { throw new AssertionError("Escrita inesperada."); }
        public void update(Product produto) { throw new AssertionError("Escrita inesperada."); }
        public void registrarEntrada(EntradaEstoque entrada) { throw new AssertionError("Entrada inesperada."); }
        public void desativarPorId(int id) { throw new AssertionError("Desativação inesperada."); }
        public void deleteById(Integer id) { throw new AssertionError("Exclusão inesperada."); }
        public int contarUsos(int id) { throw new AssertionError("Consulta de histórico inesperada."); }
        public Product instantiate(ResultSet rs) { throw new AssertionError("JDBC inesperado."); }
    }

    private static class Tela {
        final FXMLLoader loader;
        final PDVController controller;
        final Scene cena;

        Tela(Produtos produtos) throws Exception {
            loader = new FXMLLoader(PDVController.class.getResource("/com/casarural/sistemapdv/view/pdv.fxml"));
            Parent root = loader.load();
            cena = new Scene(root);
            Stage janela = new Stage();
            janela.setScene(cena);
            controller = loader.getController();
            controller.setProductService(new ProductService(produtos));
        }

        TextField campo(String nome) { return (TextField) loader.getNamespace().get(nome); }
        Label rotulo(String nome) { return (Label) loader.getNamespace().get(nome); }
        Button botao(String nome) { return (Button) loader.getNamespace().get(nome); }
        @SuppressWarnings("unchecked") ListView<OrderItem> itens() { return (ListView<OrderItem>) loader.getNamespace().get("listaItens"); }

        void preencher(String quantidade) {
            campo("campoCodigoProduto").setText("900001");
            campo("campoQuantidade").setText(quantidade);
        }
        void adicionar(String quantidade) { preencher(quantidade); botao("botaoAdicionar").fire(); }
        void enter(String quantidade) {
            preencher(quantidade);
            TextField codigo = campo("campoCodigoProduto");
            codigo.getOnAction().handle(new ActionEvent(codigo, codigo));
        }
    }

    private static DialogPane aviso() {
        for (Window janela : Window.getWindows()) {
            if (janela.getScene() != null && janela.getScene().getRoot() instanceof DialogPane) {
                return (DialogPane) janela.getScene().getRoot();
            }
        }
        throw new AssertionError("Aviso esperado não foi aberto.");
    }

    private static void fecharAviso() {
        DialogPane aviso = aviso();
        ((Button) aviso.lookupButton(aviso.getButtonTypes().get(0))).fire();
    }

    private static void responderBalanca(String valor) {
        Platform.runLater(() -> {
            DialogPane dialogo = aviso();
            if (valor != null) ((TextField) dialogo.lookup(".text-field")).setText(valor);
            ButtonBar.ButtonData tipo = valor == null ? ButtonBar.ButtonData.CANCEL_CLOSE : ButtonBar.ButtonData.OK_DONE;
            ButtonType resposta = dialogo.getButtonTypes().stream()
                    .filter(b -> b.getButtonData() == tipo).findFirst().orElseThrow();
            ((Button) dialogo.lookupButton(resposta)).fire();
        });
    }

    @Test
    void quantidadeInvalidaNaoConsultaProdutoNemIncluiUmaUnidadePorEngano() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            Tela tela = new Tela(produtos);
            for (String quantidade : List.of("0", "-1", "1,5", "1.5", "abc", "2147483648")) {
                tela.adicionar(quantidade);
                assertTrue(tela.itens().getItems().isEmpty());
                assertEquals(0, produtos.consultasCodigo);
                assertEquals(0, produtos.consultasLista);
                assertEquals(quantidade, tela.campo("campoQuantidade").getText());
                assertEquals("900001", tela.campo("campoCodigoProduto").getText());
                assertEquals("R$ 0,00", tela.rotulo("labelTotal").getText());
                assertTrue(aviso().getContentText().contains("quantidade inteira entre 1"));
                fecharAviso();
            }
        });
    }

    @Test
    void quantidadeInvalidaPeloEnterPreservaOCarrinhoExistente() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            Tela tela = new Tela(produtos);
            tela.enter("2");
            OrderItem original = tela.itens().getItems().get(0);
            String total = tela.rotulo("labelTotal").getText();
            tela.enter("0");
            assertEquals(1, produtos.consultasCodigo);
            assertEquals(1, tela.itens().getItems().size());
            assertSame(original, tela.itens().getItems().get(0));
            assertEquals(2, original.getQtd());
            assertEquals(total, tela.rotulo("labelTotal").getText());
            assertEquals("0", tela.campo("campoQuantidade").getText());
        });
    }

    @Test
    void buscaPorBotaoOuF2TambemValidaQuantidadeAntesDeConsultarDao() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            Tela tela = new Tela(produtos);
            tela.preencher("texto");
            tela.botao("botaoBuscar").fire();
            assertEquals(0, produtos.consultasLista);
            assertEquals(0, produtos.consultasCodigo);
            fecharAviso();
            tela.campo("campoQuantidade").setText("-5");
            Event.fireEvent(tela.cena, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F2, false, false, false, false));
            assertEquals(0, produtos.consultasLista);
            assertEquals(0, produtos.consultasCodigo);
            assertTrue(tela.itens().getItems().isEmpty());
            assertTrue(aviso().getContentText().contains("quantidade inteira"));
        });
    }

    @Test
    void vazioMantemPadraoDoLeitorEQuantidadePositivaContinuaSomando() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            Tela tela = new Tela(produtos);
            tela.enter("");
            tela.enter("   ");
            tela.adicionar(" 3 ");
            assertEquals(3, produtos.consultasCodigo);
            assertEquals(1, tela.itens().getItems().size());
            assertEquals(5, tela.itens().getItems().get(0).getQtd());
            assertEquals("R$ 12,50", tela.rotulo("labelTotal").getText());
            assertEquals("1", tela.campo("campoQuantidade").getText());
            assertEquals("", tela.campo("campoCodigoProduto").getText());
            assertEquals(20, produtos.produto.getEstoque(), "Adicionar ao cupom não deve baixar estoque antes da venda");
        });
    }

    @Test
    void somaAcimaDeIntRecusaSemAlterarQuantidadeTotalOuEstoque() throws Exception {
        JavafxTestSupport.executar(() -> {
            for (boolean controlaEstoque : List.of(true, false)) {
                Produtos produtos = new Produtos();
                produtos.produto.setEstoque(Integer.MAX_VALUE);
                produtos.produto.setControlaEstoque(controlaEstoque);
                Tela tela = new Tela(produtos);
                tela.adicionar(String.valueOf(Integer.MAX_VALUE));
                assertEquals(Integer.MAX_VALUE, tela.itens().getItems().get(0).getQtd());
                String total = tela.rotulo("labelTotal").getText();
                tela.adicionar("1");
                assertEquals(1, tela.itens().getItems().size());
                assertEquals(Integer.MAX_VALUE, tela.itens().getItems().get(0).getQtd());
                assertEquals(total, tela.rotulo("labelTotal").getText());
                assertEquals(Integer.MAX_VALUE, produtos.produto.getEstoque());
                assertTrue(aviso().getContentText().contains("quantidade total deste produto"));
                fecharAviso();
            }
        });
    }

    @Test
    void quantidadeValidaContinuaRespeitandoEstoqueDisponivel() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            produtos.produto.setEstoque(3);
            Tela tela = new Tela(produtos);
            tela.adicionar("2");
            tela.adicionar("2");
            assertEquals(2, produtos.consultasCodigo);
            assertEquals(2, tela.itens().getItems().get(0).getQtd());
            assertEquals("R$ 5,00", tela.rotulo("labelTotal").getText());
            assertTrue(aviso().getContentText().contains("Estoque disponível"));
        });
    }

    @Test
    void trocaPreservaItensDianteDeQuantidadeInvalidaEUsaEstoqueDevolvido() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            produtos.produto.setEstoque(1);
            Tela tela = new Tela(produtos);
            VendaParaDesfazer original = new VendaParaDesfazer();
            original.setIdPedido(80);
            original.getItens().add(new OrderItem(produtos.produto, 2, produtos.produto.getPrecoProduto()));
            original.getItens().get(0).setEstoqueBaixado(2);
            int[] concluiu = {0};
            tela.controller.iniciarTroca(original, () -> concluiu[0]++);
            tela.adicionar("0");
            assertEquals(0, produtos.consultasCodigo);
            assertEquals(2, tela.itens().getItems().get(0).getQtd());
            fecharAviso();
            tela.adicionar("1");
            assertEquals(3, tela.itens().getItems().get(0).getQtd(), "As duas unidades devolvidas continuam disponíveis na troca");
            assertEquals(2, original.getItens().get(0).getQtd());
            assertEquals(0, concluiu[0]);
            assertEquals("R$ 7,50", tela.rotulo("labelTotal").getText());
        });
    }

    @Test
    void pesavelValidaQuantidadeAntesDaConsultaEMantemUmaLinhaPorPesagem() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            produtos.produto.setTipoVenda(TipoVenda.PESAVEL);
            produtos.produto.setEstoque(0);
            Tela tela = new Tela(produtos);
            tela.adicionar("0");
            assertEquals(0, produtos.consultasCodigo);
            assertTrue(tela.itens().getItems().isEmpty());
            fecharAviso();
            responderBalanca("12,40");
            tela.adicionar("");
            responderBalanca("5,10");
            tela.adicionar("2");
            assertEquals(2, produtos.consultasCodigo);
            assertEquals(2, tela.itens().getItems().size());
            assertTrue(tela.itens().getItems().stream().allMatch(i -> i.getQtd() == 1));
            assertEquals(Money.parse("12,40"), tela.itens().getItems().get(0).getPrecoUnitario());
            assertEquals(Money.parse("5,10"), tela.itens().getItems().get(1).getPrecoUnitario());
            assertEquals("R$ 17,50", tela.rotulo("labelTotal").getText());
            assertEquals(0, produtos.produto.getEstoque());
        });
    }

    @Test
    void cancelarPesagemNaoIncluiItemNemConverteQuantidadeEmUmaUnidade() throws Exception {
        JavafxTestSupport.executar(() -> {
            Produtos produtos = new Produtos();
            produtos.produto.setTipoVenda(TipoVenda.PESAVEL);
            Tela tela = new Tela(produtos);
            responderBalanca(null);
            tela.adicionar("3");
            assertTrue(tela.itens().getItems().isEmpty());
            assertEquals("R$ 0,00", tela.rotulo("labelTotal").getText());
            assertEquals("3", tela.campo("campoQuantidade").getText());
            assertEquals("900001", tela.campo("campoCodigoProduto").getText());
        });
    }
}
