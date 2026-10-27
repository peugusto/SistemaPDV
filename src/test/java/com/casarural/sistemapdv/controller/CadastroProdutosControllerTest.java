package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Money;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Usa o FXML/controller reais e produtos em memoria, sem conexao ao banco. */
class CadastroProdutosControllerTest {
    private static boolean javafxIniciado;

    @BeforeAll
    static void iniciarJavafx() throws Exception {
        boolean linuxSemTela = System.getProperty("os.name", "").startsWith("Linux")
                && System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null;
        assumeFalse(Boolean.getBoolean("java.awt.headless") || linuxSemTela,
                "Requer ambiente grafico para JavaFX.");
        CountDownLatch pronto = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            pronto.countDown();
        });
        assertTrue(pronto.await(10, TimeUnit.SECONDS), "JavaFX nao iniciou.");
        javafxIniciado = true;
    }

    @AfterAll
    static void encerrarJavafx() {
        if (javafxIniciado) Platform.exit();
    }

    @FunctionalInterface
    private interface AcaoFx { void executar() throws Exception; }

    private static void noJavafx(AcaoFx acao) throws Exception {
        FutureTask<Void> tarefa = new FutureTask<>(() -> {
            try {
                acao.executar();
            } finally {
                fecharJanelas();
            }
            return null;
        });
        Platform.runLater(tarefa);
        try {
            tarefa.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error) throw (Error) e.getCause();
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        } catch (java.util.concurrent.TimeoutException e) {
            Platform.runLater(CadastroProdutosControllerTest::fecharJanelas);
            throw e;
        }
    }

    private static void fecharJanelas() {
        for (Window janela : new ArrayList<>(Window.getWindows())) {
            if (janela instanceof Stage) ((Stage) janela).close();
        }
    }

    private static DialogPane aviso() {
        for (Window janela : Window.getWindows()) {
            if (janela.getScene() != null
                    && janela.getScene().getRoot() instanceof DialogPane) {
                return (DialogPane) janela.getScene().getRoot();
            }
        }
        throw new AssertionError("Aviso esperado nao foi aberto.");
    }

    private static class Tela {
        final FXMLLoader loader;
        final CadastroProdutosController controller;

        Tela(ProdutosEmMemoria dao) throws Exception {
            loader = new FXMLLoader(CadastroProdutosController.class.getResource(
                    "/com/casarural/sistemapdv/view/CadastroProduto.fxml"));
            Parent root = loader.load();
            Stage janela = new Stage();
            janela.setScene(new Scene(root));
            controller = loader.getController();
            controller.setProductService(new ProductService(dao));
            controller.setProduct(new Product());
        }

        TextField campo(String nome) { return (TextField) loader.getNamespace().get(nome); }

        void pesquisar(String codigo) {
            campo("txtCodBarras").setText(codigo);
            campo("txtCodBarras").getOnAction().handle(new ActionEvent());
        }

        void pesquisarNovo(String codigo, boolean confirmar) {
            Platform.runLater(() -> {
                DialogPane dialogo = aviso();
                ButtonBar.ButtonData tipo = confirmar
                        ? ButtonBar.ButtonData.OK_DONE : ButtonBar.ButtonData.CANCEL_CLOSE;
                ButtonType resposta = dialogo.getButtonTypes().stream()
                        .filter(b -> b.getButtonData() == tipo).findFirst().orElseThrow();
                ((Button) dialogo.lookupButton(resposta)).fire();
            });
            pesquisar(codigo);
        }

        void preencherNovo() {
            campo("txtNome").setText("Feijao");
            campo("txtPreco").setText("12,50");
            campo("txtEstoque").setText("3");
        }
    }

    /** Copias simulam o JDBC: alterar objeto carregado nao altera o registro antes do update. */
    private static class ProdutosEmMemoria implements ProductDao {
        final Map<Integer, Product> registros = new LinkedHashMap<>();
        int insercoes;
        int atualizacoes;
        int proximoId = 2;

        ProdutosEmMemoria(String codigo) {
            Product arroz = new Product(codigo, "Arroz", Money.parse("8"), 10);
            arroz.setIdProduto(1);
            registros.put(1, copiar(arroz));
        }

        public void insert(Product obj) {
            insercoes++;
            obj.setIdProduto(proximoId++);
            registros.put(obj.getIdProduto(), copiar(obj));
        }
        public void update(Product obj) {
            atualizacoes++;
            assertTrue(registros.containsKey(obj.getIdProduto()));
            registros.put(obj.getIdProduto(), copiar(obj));
        }
        public Optional<Product> findByCodBarras(String codigo) {
            return registros.values().stream()
                    .filter(p -> p.getCodBarras().equalsIgnoreCase(codigo)).findFirst()
                    .map(CadastroProdutosControllerTest::copiar);
        }
        public Optional<Product> findById(Integer id) {
            return Optional.ofNullable(registros.get(id)).map(CadastroProdutosControllerTest::copiar);
        }
        public List<Product> findAll() {
            List<Product> resultado = new ArrayList<>();
            registros.values().forEach(p -> resultado.add(copiar(p)));
            return resultado;
        }
        public void deleteById(Integer id) { throw new AssertionError("Exclusao inesperada."); }
        public Product instantiate(ResultSet rs) { throw new AssertionError("JDBC inesperado."); }
        public int contarUsos(int id) { return 0; }
    }

    private static Product copiar(Product original) {
        Product copia = new Product(original.getCodBarras(), original.getNomeProduto(),
                original.getPrecoProduto(), original.getEstoque());
        copia.setIdProduto(original.getIdProduto());
        copia.setPrecoCusto(original.getPrecoCusto());
        copia.setPermiteFiado(original.isPermiteFiado());
        copia.setTipoVenda(original.getTipoVenda());
        copia.setControlaEstoque(original.isControlaEstoque());
        copia.setAtivo(original.isAtivo());
        return copia;
    }

    private static void originalPreservado(ProdutosEmMemoria dao) {
        Product original = dao.registros.get(1);
        assertEquals("900001", original.getCodBarras());
        assertEquals("Arroz", original.getNomeProduto());
        assertEquals(Money.parse("8"), original.getPrecoProduto());
        assertEquals(10, original.getEstoque());
    }

    @Test
    void existenteDepoisNovoInsereOutroIdEPreservaOAnterior() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.pesquisarNovo("900002", true);
            tela.preencherNovo();
            tela.controller.onBotaoSalvarAction();

            assertEquals(1, dao.insercoes);
            assertEquals(0, dao.atualizacoes);
            originalPreservado(dao);
            Product novo = dao.findByCodBarras("900002").orElseThrow();
            assertNotEquals(1, novo.getIdProduto());
            assertEquals("Feijao", novo.getNomeProduto());
            assertEquals(3, novo.getEstoque());
        });
    }

    @Test
    void codigoEditadoSemEnterNaoGravaNemAlteraOProdutoAnterior() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.campo("txtCodBarras").setText("900002");
            tela.campo("txtEstoque").setText("5");
            tela.controller.onBotaoSalvarAction();

            assertEquals(0, dao.insercoes);
            assertEquals(0, dao.atualizacoes);
            originalPreservado(dao);
            assertTrue(aviso().getContentText().contains("Aperte Enter"));
        });
    }

    @Test
    void pesquisaCarregaCodigoCanonicoAntesDeValidarOSalvamento() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("ABC123");
            Tela tela = new Tela(dao);
            tela.pesquisar("abc123");
            assertEquals("ABC123", tela.campo("txtCodBarras").getText());
            tela.campo("txtEstoque").setText("2");
            tela.controller.onBotaoSalvarAction();

            assertEquals(0, dao.insercoes);
            assertEquals(1, dao.atualizacoes);
            assertEquals("ABC123", dao.registros.get(1).getCodBarras());
            assertEquals(12, dao.registros.get(1).getEstoque());
        });
    }

    @Test
    void espacosNoCodigoNaoBloqueiamEntradaNoMesmoProduto() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.campo("txtCodBarras").setText(" 900001 ");
            tela.campo("txtEstoque").setText("2");
            tela.controller.onBotaoSalvarAction();

            assertEquals(0, dao.insercoes);
            assertEquals(1, dao.atualizacoes);
            assertEquals("900001", dao.registros.get(1).getCodBarras());
            assertEquals(12, dao.registros.get(1).getEstoque());
        });
    }

    @Test
    void recusarNovoCadastroNaoPermiteSalvarSobreOAnterior() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.pesquisarNovo("900002", false);
            tela.controller.onBotaoSalvarAction();

            assertEquals(0, dao.insercoes);
            assertEquals(0, dao.atualizacoes);
            originalPreservado(dao);
            assertFalse(dao.findByCodBarras("900002").isPresent());
        });
    }

    @Test
    void primeiroCadastroSemPesquisaAnteriorContinuaInserindo() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisarNovo("900002", true);
            tela.preencherNovo();
            tela.controller.onBotaoSalvarAction();

            assertEquals(1, dao.insercoes);
            assertEquals(0, dao.atualizacoes);
            originalPreservado(dao);
            assertTrue(dao.findByCodBarras("900002").isPresent());
        });
    }
}
