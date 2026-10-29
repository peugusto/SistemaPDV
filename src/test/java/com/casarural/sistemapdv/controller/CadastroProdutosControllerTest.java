package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.EntradaEstoque;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.ProdutoCampos;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
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
import javafx.scene.control.CheckBox;
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
import java.util.UUID;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Usa o FXML/controller reais e produtos em memoria, sem conexao ao banco. */
@org.junit.jupiter.api.extension.ExtendWith(JavafxTestSupport.class)
class CadastroProdutosControllerTest {
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
        int entradas;
        boolean falharAntes;
        boolean falharDepoisDoCommit;
        boolean falharConexao;
        boolean falharAtualizacao;
        int consultasCodigo;
        int usos;
        int desativacoes;
        Product ultimoCarregado;
        final Map<UUID, EntradaEstoque> operacoes = new LinkedHashMap<>();
        final List<EntradaEstoque> tentativas = new ArrayList<>();

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
            if (falharAtualizacao) {
                falharAtualizacao = false;
                throw new DbException("Falha simulada antes de atualizar o produto.");
            }
            assertTrue(registros.containsKey(obj.getIdProduto()));
            registros.put(obj.getIdProduto(), copiar(obj));
        }
        public void desativarPorId(int idProduto) {
            desativacoes++;
            Product atual = copiar(registros.get(idProduto));
            atual.setAtivo(false);
            registros.put(idProduto, atual);
        }
        public void registrarEntrada(EntradaEstoque entrada) {
            tentativas.add(entrada);
            if (falharConexao) throw new DbException("Sem conexão.");
            if (falharAntes) {
                falharAntes = false;
                throw new DbException("Falha simulada antes de gravar; rollback confirmado.");
            }
            EntradaEstoque anterior = operacoes.get(entrada.getIdOperacao());
            if (anterior != null) {
                assertTrue(anterior.mesmosDados(entrada.getIdProduto(), entrada.getQuantidade(), entrada.getCustoInformado()));
                return;
            }
            Product atual = copiar(registros.get(entrada.getIdProduto()));
            atual.setEstoque(Math.addExact(atual.getEstoque(), entrada.getQuantidade()));
            if (entrada.getCustoInformado() != null) atual.setPrecoCusto(entrada.getCustoInformado());
            registros.put(atual.getIdProduto(), atual);
            operacoes.put(entrada.getIdOperacao(), entrada);
            entradas++;
            if (falharDepoisDoCommit) {
                falharDepoisDoCommit = false;
                throw new ResultadoTransacaoIncertoException("entrada de estoque", new SQLException("Resposta perdida."));
            }
        }
        public Optional<Product> findByCodBarras(String codigo) {
            consultasCodigo++;
            return registros.values().stream()
                    .filter(p -> p.getCodBarras().equalsIgnoreCase(codigo)).findFirst()
                    .map(p -> { ultimoCarregado = copiar(p); return ultimoCarregado; });
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
        public int contarUsos(int id) { return usos; }
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

    private static class ListaEditada extends ProductListController {
        int atualizacoes;
        boolean falhar;

        @Override public void updateTableView() {
            atualizacoes++;
            if (falhar) throw new DbException("Falha simulada ao consultar a lista.");
        }
    }

    private static class TelaEdicao {
        final FXMLLoader loader;
        final ProductFormController controller;
        final ListaEditada lista;

        TelaEdicao(ProdutosEmMemoria dao, Product original) throws Exception {
            loader = new FXMLLoader(ProductFormController.class.getResource(
                    "/com/casarural/sistemapdv/view/ProductForm.fxml"));
            Parent root = loader.load();
            Stage janela = new Stage();
            janela.setScene(new Scene(root));
            controller = loader.getController();
            lista = new ListaEditada();
            controller.setProduct(original);
            controller.setServices(new ProductService(dao), lista);
            controller.updateFormData();
        }

        TextField campo(String nome) { return (TextField) loader.getNamespace().get(nome); }
        CheckBox opcao(String nome) { return (CheckBox) loader.getNamespace().get(nome); }
        Button botao(String nome) { return (Button) loader.getNamespace().get(nome); }
        void salvar() { botao("btnSave").fire(); }
        void cancelar() { botao("btnCancel").fire(); }
    }

    private static void produtoPreservado(Product esperado, Product atual) {
        assertEquals(esperado.getIdProduto(), atual.getIdProduto());
        assertEquals(esperado.getCodBarras(), atual.getCodBarras());
        assertEquals(esperado.getNomeProduto(), atual.getNomeProduto());
        assertEquals(esperado.getPrecoProduto(), atual.getPrecoProduto());
        assertEquals(esperado.getEstoque(), atual.getEstoque());
        assertEquals(esperado.getPrecoCusto(), atual.getPrecoCusto());
        assertEquals(esperado.getTipoVenda(), atual.getTipoVenda());
        assertEquals(esperado.isPermiteFiado(), atual.isPermiteFiado());
        assertEquals(esperado.isControlaEstoque(), atual.isControlaEstoque());
        assertEquals(esperado.isAtivo(), atual.isAtivo());
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
            assertEquals(0, dao.atualizacoes);
            assertEquals(1, dao.entradas);
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
            assertEquals(0, dao.atualizacoes);
            assertEquals(1, dao.entradas);
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

    private static void fecharAviso() {
        DialogPane pane = aviso();
        ((Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire();
    }

    @Test
    void retryAposFalhaAntesDeGravarNaoMutaObjetoNemDuplicaEntrada() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.campo("txtEstoque").setText("5");
            dao.falharAntes = true;
            tela.controller.onBotaoSalvarAction();
            assertEquals(10, dao.registros.get(1).getEstoque());
            assertEquals(10, dao.ultimoCarregado.getEstoque());
            assertFalse(tela.campo("txtEstoque").isDisabled());
            fecharAviso();
            tela.controller.onBotaoSalvarAction();
            assertEquals(15, dao.registros.get(1).getEstoque());
            assertEquals(10, dao.ultimoCarregado.getEstoque());
            assertEquals(1, dao.entradas);
            assertEquals(dao.tentativas.get(0).getIdOperacao(), dao.tentativas.get(1).getIdOperacao());
        });
    }

    @Test
    void resultadoIncertoMantemChaveMesmoSeNovaConexaoFalharERetryNaoDuplica() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.campo("txtEstoque").setText("5");
            dao.falharDepoisDoCommit = true;
            tela.controller.onBotaoSalvarAction();
            assertEquals(15, dao.registros.get(1).getEstoque());
            assertTrue(tela.campo("txtEstoque").isDisabled());
            assertTrue(tela.campo("txtCodBarras").isDisabled());
            assertTrue(tela.campo("txtCusto").isDisabled());
            fecharAviso();
            dao.falharConexao = true;
            tela.controller.onBotaoSalvarAction();
            assertTrue(tela.campo("txtEstoque").isDisabled());
            fecharAviso();
            dao.falharConexao = false;
            tela.controller.onBotaoSalvarAction();
            assertEquals(15, dao.registros.get(1).getEstoque());
            assertEquals(1, dao.entradas);
            assertEquals(1, dao.tentativas.stream().map(EntradaEstoque::getIdOperacao).distinct().count());
        });
    }

    @Test
    void corrigirQuantidadeAposFalhaConfirmadaCriaOutraChaveSemAlterarCarregado() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.campo("txtEstoque").setText("5");
            dao.falharAntes = true;
            tela.controller.onBotaoSalvarAction();
            fecharAviso();
            tela.campo("txtEstoque").setText("3");
            tela.controller.onBotaoSalvarAction();
            assertEquals(13, dao.registros.get(1).getEstoque());
            assertNotEquals(dao.tentativas.get(0).getIdOperacao(), dao.tentativas.get(1).getIdOperacao());
            assertEquals(10, dao.ultimoCarregado.getEstoque());
        });
    }

    @Test
    void naoReenviaPayloadEditadoEnquantoTentativaEstaIncerta() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            tela.campo("txtEstoque").setText("5");
            dao.falharDepoisDoCommit = true;
            tela.controller.onBotaoSalvarAction();
            fecharAviso();
            // Campos estão desabilitados; simula também uma alteração por código/evento externo.
            tela.campo("txtEstoque").setText("6");
            tela.controller.onBotaoSalvarAction();
            assertEquals(1, dao.tentativas.size());
            assertEquals(15, dao.registros.get(1).getEstoque());
            fecharAviso();
            tela.campo("txtEstoque").setText("5");
            tela.controller.onBotaoSalvarAction();
            assertEquals(2, dao.tentativas.size());
            assertEquals(1, dao.entradas);
            assertEquals(15, dao.registros.get(1).getEstoque());
        });
    }

    @Test
    void entradaUsaSaldoAtualEPreservaNomePrecoECustoQueOutroTerminalAlterou() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            dao.registros.get(1).setPrecoCusto(Money.parse("4"));
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            Product atual = dao.registros.get(1);
            atual.setEstoque(9);
            atual.setNomeProduto("Arroz novo");
            atual.setPrecoProduto(Money.parse("9"));
            atual.setPrecoCusto(Money.parse("6"));
            tela.campo("txtEstoque").setText("5");
            tela.controller.onBotaoSalvarAction();
            Product salvo = dao.registros.get(1);
            assertEquals(14, salvo.getEstoque());
            assertEquals("Arroz novo", salvo.getNomeProduto());
            assertEquals(Money.parse("9"), salvo.getPrecoProduto());
            assertEquals(Money.parse("6"), salvo.getPrecoCusto());
            assertNull(dao.tentativas.get(0).getCustoInformado());
            assertEquals(0, dao.atualizacoes);
        });
    }

    @Test
    void quantidadeOuCustoInvalidoNaoChamamDaoNemTravamCampos() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Tela tela = new Tela(dao);
            tela.pesquisar("900001");
            for (String quantidade : List.of("0", "2147483648")) {
                tela.campo("txtEstoque").setText(quantidade);
                tela.controller.onBotaoSalvarAction();
                assertTrue(dao.tentativas.isEmpty());
                assertFalse(tela.campo("txtEstoque").isDisabled());
                fecharAviso();
            }
            tela.campo("txtEstoque").setText("5");
            for (String custo : List.of(".", "100000000")) {
                tela.campo("txtCusto").setText(custo);
                tela.controller.onBotaoSalvarAction();
                assertTrue(dao.tentativas.isEmpty());
                assertFalse(tela.campo("txtEstoque").isDisabled());
                fecharAviso();
            }
            assertEquals(10, dao.ultimoCarregado.getEstoque());
        });
    }

    @Test
    void edicaoRecusaEstoqueNegativoForaDeIntOuFracionadoAntesDeConsultarDao() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Product original = dao.findById(1).orElseThrow();
            Product antes = copiar(original);
            TelaEdicao tela = new TelaEdicao(dao, original);
            tela.campo("txtNome").setText("Arroz alterado");
            for (String estoque : List.of("-5", "2147483648", "1,5", "abc")) {
                tela.campo("txtEstoque").setText(estoque);
                tela.salvar();
                assertEquals(estoque, tela.campo("txtEstoque").getText(), "O texto inválido não deve ser revertido silenciosamente");
                assertEquals(0, dao.consultasCodigo);
                assertEquals(0, dao.atualizacoes);
                assertTrue(aviso().getContentText().contains("estoque inteiro entre 0"));
                produtoPreservado(antes, original);
                produtoPreservado(antes, dao.registros.get(1));
                fecharAviso();
            }
        });
    }

    @Test
    void edicaoRecusaCustoMalformadoSemApagarOCustoAnteriorNemChamarDao() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            dao.registros.get(1).setPrecoCusto(Money.parse("4"));
            Product original = dao.findById(1).orElseThrow();
            Product antes = copiar(original);
            TelaEdicao tela = new TelaEdicao(dao, original);
            tela.campo("txtNome").setText("Arroz alterado");
            for (String custo : List.of(".", ",", "100000000")) {
                tela.campo("txtCusto").setText(custo);
                tela.salvar();
                assertEquals(custo, tela.campo("txtCusto").getText());
                assertEquals(0, dao.consultasCodigo);
                assertEquals(0, dao.atualizacoes);
                assertTrue(aviso().getContentText().toLowerCase().contains("custo"));
                produtoPreservado(antes, original);
                produtoPreservado(antes, dao.registros.get(1));
                fecharAviso();
            }
        });
    }

    @Test
    void edicaoDistingueCustoVazioZeroEValidoEPreservaTodosOsDemaisCampos() throws Exception {
        noJavafx(() -> {
            for (String custo : List.of("", "0", "4,25", ".50")) {
                ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
                dao.registros.get(1).setPrecoCusto(Money.parse("5"));
                Product original = dao.findById(1).orElseThrow();
                Product antes = copiar(original);
                TelaEdicao tela = new TelaEdicao(dao, original);
                tela.campo("txtNome").setText("Arroz novo");
                tela.campo("txtEstoque").setText("0");
                tela.campo("txtCusto").setText(custo);
                tela.opcao("chkPermiteFiado").setSelected(false);
                tela.opcao("chkAtivo").setSelected(false);
                tela.opcao("chkPesavel").setSelected(true);
                tela.salvar();
                assertEquals(1, dao.atualizacoes);
                Product salvo = dao.registros.get(1);
                assertEquals(custo.isEmpty() ? null : Money.parse(custo), salvo.getPrecoCusto());
                assertEquals(1, salvo.getIdProduto());
                assertEquals("900001", salvo.getCodBarras());
                assertEquals("Arroz novo", salvo.getNomeProduto());
                assertEquals(Money.parse("8"), salvo.getPrecoProduto());
                assertEquals(0, salvo.getEstoque());
                assertEquals(TipoVenda.PESAVEL, salvo.getTipoVenda());
                assertFalse(salvo.isPermiteFiado());
                assertFalse(salvo.isAtivo());
                assertFalse(salvo.isControlaEstoque());
                produtoPreservado(antes, original);
                assertEquals(1, tela.lista.atualizacoes);
                assertTrue(tela.botao("btnSave").isDisabled());
                fecharAviso();
            }
        });
    }

    @Test
    void cancelarDepoisDeFalhaNaEdicaoNaoAlteraObjetoDaListaNemRegistro() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            dao.registros.get(1).setPrecoCusto(Money.parse("4"));
            Product original = dao.findById(1).orElseThrow();
            Product antes = copiar(original);
            TelaEdicao tela = new TelaEdicao(dao, original);
            tela.campo("txtNome").setText("Nome que nao foi salvo");
            tela.campo("txtCodBarras").setText("900099");
            tela.campo("txtPreco").setText("20");
            tela.campo("txtEstoque").setText("2");
            tela.campo("txtCusto").setText("");
            tela.opcao("chkAtivo").setSelected(false);
            tela.opcao("chkPermiteFiado").setSelected(false);
            dao.falharAtualizacao = true;
            tela.salvar();
            assertEquals(1, dao.atualizacoes);
            produtoPreservado(antes, original);
            produtoPreservado(antes, dao.registros.get(1));
            assertEquals(0, tela.lista.atualizacoes);
            assertFalse(tela.botao("btnSave").isDisabled());
            assertEquals("Nome que nao foi salvo", tela.campo("txtNome").getText());
            fecharAviso();
            tela.cancelar();
            assertEquals(1, dao.atualizacoes);
            produtoPreservado(antes, original);
            produtoPreservado(antes, dao.registros.get(1));
        });
    }

    @Test
    void retryDaEdicaoAposFalhaConfirmadaGravaUmaCopiaSemMutarOOriginal() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Product original = dao.findById(1).orElseThrow();
            Product antes = copiar(original);
            TelaEdicao tela = new TelaEdicao(dao, original);
            tela.campo("txtNome").setText("Arroz confirmado");
            tela.campo("txtEstoque").setText("5");
            dao.falharAtualizacao = true;
            tela.salvar();
            produtoPreservado(antes, original);
            fecharAviso();
            tela.salvar();
            assertEquals(2, dao.atualizacoes);
            assertEquals("Arroz confirmado", dao.registros.get(1).getNomeProduto());
            assertEquals(5, dao.registros.get(1).getEstoque());
            assertEquals(1, tela.lista.atualizacoes);
            produtoPreservado(antes, original);
        });
    }

    @Test
    void falhaDeRefreshDepoisDaEdicaoConfirmaSucessoEImpedeNovaGravacao() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            Product original = dao.findById(1).orElseThrow();
            TelaEdicao tela = new TelaEdicao(dao, original);
            tela.campo("txtNome").setText("Arroz salvo");
            tela.lista.falhar = true;
            tela.salvar();
            assertEquals(1, dao.atualizacoes);
            assertEquals("Arroz salvo", dao.registros.get(1).getNomeProduto());
            assertTrue(tela.botao("btnSave").isDisabled());
            assertTrue(aviso().getHeaderText().contains("O produto foi salvo"));
            assertTrue(aviso().getContentText().contains("Não salve novamente"));
            fecharAviso();
            // Mesmo um evento externo disparado diretamente não pode repetir a atualização confirmada.
            Button salvar = tela.botao("btnSave");
            tela.controller.onBtnSaveAction(new ActionEvent(salvar, salvar));
            assertEquals(1, dao.atualizacoes);
            assertEquals(1, tela.lista.atualizacoes);
            tela.cancelar();
        });
    }

    @Test
    void parserDeCustoParaSalvarEhEstritoMasPreviewContinuaPermissivo() throws Exception {
        noJavafx(() -> {
            TextField campo = new TextField();
            for (String invalido : List.of(".", ",", "abc", "1,234", "-1")) {
                campo.setText(invalido);
                assertThrows(IllegalArgumentException.class, () -> ProdutoCampos.lerValorParaSalvar(campo));
                assertEquals(invalido, campo.getText());
            }
            campo.setText(".");
            assertNull(ProdutoCampos.lerValor(campo));
            campo.setText(" ");
            assertNull(ProdutoCampos.lerValorParaSalvar(campo));
            campo.setText("0");
            assertEquals(Money.ZERO, ProdutoCampos.lerValorParaSalvar(campo));
            campo.setText("99999999,99");
            assertEquals(Money.parse("99999999,99"), ProdutoCampos.lerValorParaSalvar(campo));
        });
    }

    @Test
    void desativacaoConfirmadaComRefreshFalhoMantemSucessoEObjetoInativo() throws Exception {
        noJavafx(() -> {
            ProdutosEmMemoria dao = new ProdutosEmMemoria("900001");
            dao.usos = 1;
            Product original = dao.findById(1).orElseThrow();
            ProductListController lista = new ProductListController() {
                @Override public void updateTableView() { throw new DbException("Consulta falhou depois da desativação."); }
            };
            FXMLLoader loader = new FXMLLoader(ProductListController.class.getResource(
                    "/com/casarural/sistemapdv/view/product_list.fxml"));
            loader.setControllerFactory(tipo -> lista);
            Parent root = loader.load();
            Stage janela = new Stage();
            janela.setScene(new Scene(root));
            lista.setProductService(new ProductService(dao));
            Platform.runLater(() -> {
                DialogPane dialogo = aviso();
                ButtonType desativar = dialogo.getButtonTypes().stream()
                        .filter(b -> b.getButtonData() == ButtonBar.ButtonData.OTHER).findFirst().orElseThrow();
                ((Button) dialogo.lookupButton(desativar)).fire();
            });
            java.lang.reflect.Method excluir = ProductListController.class.getDeclaredMethod("onDeleteAction", Product.class);
            excluir.setAccessible(true);
            excluir.invoke(lista, original);
            assertFalse(original.isAtivo());
            assertFalse(dao.registros.get(1).isAtivo());
            assertEquals(1, dao.desativacoes);
            assertEquals(0, dao.atualizacoes);
            assertTrue(aviso().getHeaderText().contains("desativado normalmente"));
            assertFalse(aviso().getHeaderText().contains("Não foi possível excluir"));
        });
    }
}
