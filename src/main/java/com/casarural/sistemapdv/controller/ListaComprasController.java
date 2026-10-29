package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.RegraListaCompras;
import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import com.casarural.sistemapdv.services.ListaComprasService;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Tema;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/** Relatórios > Lista de compras (F9). Sem PIN: só quantidades. */
public class ListaComprasController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ListaComprasController.class);

    @FXML private Label lblPeriodo, lblQtdProdutos, lblAcabaram, lblUnidades, lblCopiado, lblCusto, lblCustoDetalhe;
    @FXML private TableView<Item> tabela;
    @FXML private TableColumn<Item, Item> colComprar;
    @FXML private TableColumn<Item, String> colProduto, colPendente, colTem, colSituacao, colCustoItem;

    private ListaComprasService service;
    @FXML private Button btnImprimir, btnCopiar, btnAtualizar;
    private LocalDateTime atualizadaEm;
    private Clock relogio = Clock.systemDefaultZone();
    private Executor executor = ForkJoinPool.commonPool();
    private boolean carregando, imprimindo, dadosValidos, fechada;
    private ImpressaoVendaService impressaoVendaService = new ImpressaoVendaService();

    private final javafx.beans.value.ChangeListener<Number> quantidadeAlterada = (o, antes, agora) -> atualizarResumo();
    private final javafx.collections.ListChangeListener<Item> itensAlterados = alteracao -> {
        while (alteracao.next()) {
            for (Item i : alteracao.getRemoved()) i.comprarProperty().removeListener(quantidadeAlterada);
            for (Item i : alteracao.getAddedSubList()) i.comprarProperty().addListener(quantidadeAlterada);
        }
        atualizarResumo();
    };
    private void observarItens(javafx.collections.ObservableList<Item> itens, boolean conectar) {
        if (itens == null) return;
        if (conectar) itens.addListener(itensAlterados); else itens.removeListener(itensAlterados);
        for (Item i : itens) {
            if (conectar) i.comprarProperty().addListener(quantidadeAlterada);
            else i.comprarProperty().removeListener(quantidadeAlterada);
        }
    }
    public static void abrir(Window dono) {
        try {
            FXMLLoader loader = new FXMLLoader(ListaComprasController.class.getResource("/com/casarural/sistemapdv/view/ListaCompras.fxml"));
            Parent root = loader.load();
            ListaComprasController controller = loader.getController();
            controller.setService(new ListaComprasService(DaoFactory.createListaComprasDAO()));

            Stage stage = new Stage();
            stage.setTitle("Lista de compras - Casa Rural");
            if (dono != null) stage.initOwner(dono);
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            Tema.aplicar(scene);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) stage.close();
            });
            stage.setOnHidden(e -> controller.fechada = true);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Erro ao abrir a lista de compras", e);
            Alerts.showAlert("Lista de compras", "Não foi possível abrir a lista", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void setService(ListaComprasService service) {
        this.service = service;
        carregar();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tabela.itemsProperty().addListener((o, antes, agora) -> {
            observarItens(antes, false); observarItens(agora, true); atualizarResumo();
        });
        observarItens(tabela.getItems(), true);
        // Quantidade sempre editável: campo dentro da célula
        colComprar.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colComprar.setCellFactory(col -> new TableCell<>() {
            private final TextField campo = new TextField();
            private boolean preenchendo;
            {
                campo.setPrefWidth(80);
                campo.setTextFormatter(new TextFormatter<String>(change -> {
                    String valor = change.getControlNewText();
                    if (!valor.matches("\\d*") || valor.length() > 10) return null;
                    try { if (!valor.isEmpty()) Integer.parseInt(valor); }
                    catch (NumberFormatException erro) { return null; }
                    return change;
                }));
                campo.textProperty().addListener((obs, antes, agora) -> {
                    Item item = getItem();
                    if (item == null || preenchendo) return;
                    item.setComprar(agora == null || agora.isBlank() ? 0 : Integer.parseInt(agora));
                    lblCopiado.setText("Ajustes valem para copiar/imprimir. Atualizar recalcula as quantidades.");
                });
            }
            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    preenchendo = true;
                    try { campo.setText(String.valueOf(item.getComprar())); } finally { preenchendo = false; }
                    setGraphic(campo);
                }
            }
        });
        colProduto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduto().getNomeProduto()));
        colPendente.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getPendente())));
        colTem.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getEstoque())));
        colCustoItem.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(
                () -> c.getValue().getCustoEstimado() == null ? "—" : com.casarural.sistemapdv.util.Money.format(c.getValue().getCustoEstimado()),
                c.getValue().comprarProperty()));
        colSituacao.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().acabou() ? "Acabou" : "Repor"));
        colSituacao.setCellFactory(col -> new TableCell<>() {
            private final Label etiqueta = new Label();
            { etiqueta.getStyleClass().add("etiqueta"); }
            @Override
            protected void updateItem(String texto, boolean empty) {
                super.updateItem(texto, empty);
                setText(null);
                if (empty || texto == null) {
                    setGraphic(null);
                    return;
                }
                etiqueta.setText(texto);
                etiqueta.getStyleClass().removeAll("etiqueta-perigo", "etiqueta-alerta");
                etiqueta.getStyleClass().add("Acabou".equals(texto) ? "etiqueta-perigo" : "etiqueta-alerta");
                setGraphic(etiqueta);
            }
        });
    }

    private void carregar() {
        if (service == null || carregando || imprimindo || fechada) return;
        carregando = true;
        dadosValidos = false;
        lblCopiado.setText("Atualizando a lista...");
        atualizarBotoes();
        LocalDateTime inicio = LocalDateTime.now(relogio);
        try {
            CompletableFuture.supplyAsync(service::buscarPendencias, executor)
                    .whenComplete((itens, erro) -> javafx.application.Platform.runLater(() -> concluirCarga(itens, erro, inicio)));
        } catch (RuntimeException e) { concluirCarga(null, e, inicio); }
    }

    private void concluirCarga(List<Item> itens, Throwable erro, LocalDateTime inicio) {
        carregando = false;
        if (fechada) return;
        if (erro != null) {
            log.error("Erro ao atualizar a lista de compras", erro);
            lblCopiado.setText("Lista sem atualização. Clique em Atualizar para tentar novamente.");
            atualizarBotoes();
            Alerts.showAlert("Lista de compras", "Não foi possível atualizar a lista",
                    "Não foi possível consultar as pendências. Tente Atualizar novamente antes de copiar ou imprimir.", Alert.AlertType.ERROR);
            return;
        }
        atualizadaEm = inicio;
        tabela.setItems(FXCollections.observableArrayList(itens));
        dadosValidos = true;
        lblPeriodo.setText("Saídas pendentes de reposição · atualizado em " + inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        lblCopiado.setText("");
        atualizarResumo();
    }

    private void atualizarBotoes() {
        boolean temItens = dadosValidos && tabela.getItems().stream().anyMatch(i -> i.getComprar() > 0);
        btnImprimir.setDisable(!temItens || carregando || imprimindo);
        btnCopiar.setDisable(!temItens || carregando || imprimindo);
        btnAtualizar.setDisable(carregando || imprimindo);
        tabela.setDisable(carregando || imprimindo || !dadosValidos);
    }


    private void atualizarResumo() {
        List<Item> itens = tabela.getItems();
        long produtos = itens.stream().filter(i -> i.getComprar() > 0).count();
        lblQtdProdutos.setText(String.valueOf(produtos));
        long acabaram = itens.stream().filter(i -> i.getComprar() > 0 && i.acabou()).count();
        lblAcabaram.setText(String.valueOf(acabaram));
        Tema.estado(lblAcabaram, acabaram > 0 ? "texto-perigo" : null);
        lblUnidades.setText(String.valueOf(itens.stream().mapToLong(Item::getComprar).sum()));

        // Custo estimado: comprar x último custo; avisa quantos produtos estão sem custo
        java.math.BigDecimal total = itens.stream().map(Item::getCustoEstimado).filter(java.util.Objects::nonNull)
                .reduce(com.casarural.sistemapdv.util.Money.ZERO, java.math.BigDecimal::add);
        long semCusto = itens.stream().filter(i -> i.getComprar() > 0 && i.getProduto().getPrecoCusto() == null).count();
        atualizarBotoes();
        boolean algumCusto = itens.stream().anyMatch(i -> i.getComprar() > 0 && i.getProduto().getPrecoCusto() != null);
        lblCusto.setText(algumCusto ? com.casarural.sistemapdv.util.Money.format(total) : "—");
        lblCustoDetalhe.setText(semCusto == 0 ? "pelo último custo pago"
                : semCusto + (semCusto == 1 ? " produto sem custo" : " produtos sem custo") + " (não somados)");
    }

    @FXML
    public void onAtualizar() {
        carregar();
    }

    /** Copia as quantidades selecionadas sem registrar reposição. */
    @FXML
    public void onCopiar() {
        if (!dadosValidos || carregando || imprimindo || tabela.getItems().stream().noneMatch(i -> i.getComprar() > 0)) return;
        String texto = RegraListaCompras.texto(tabela.getItems(), atualizadaEm.toLocalDate());
        ClipboardContent conteudo = new ClipboardContent();
        conteudo.putString(texto);
        Clipboard.getSystemClipboard().setContent(conteudo);
        lblCopiado.setText("Lista copiada. Cole no WhatsApp ou no bloco de notas.");
    }
    @FXML
    public void onImprimir() {
        if (!dadosValidos || carregando || imprimindo || tabela.getItems().stream().noneMatch(i -> i.getComprar() > 0)) return;
        imprimindo = true;
        atualizarBotoes();
        try {
            impressaoVendaService.emitirListaCompras(tabela.getItems(), atualizadaEm)
                    .whenComplete((v, erro) -> finalizarImpressao(erro));
        } catch (RuntimeException erro) { finalizarImpressao(erro); }
    }
    private void finalizarImpressao(Throwable erro) {
        if (erro != null) log.error("Falha ao imprimir a lista de compras", erro);
        javafx.application.Platform.runLater(() -> {
            imprimindo = false;
            if (fechada) return;
            atualizarBotoes();
            if (erro == null) lblCopiado.setText("Lista enviada para impressão.");
            else {
                lblCopiado.setText("Não foi possível imprimir. As pendências foram mantidas.");
                Alerts.showAlert("Lista não impressa", null,
                        "Confira a impressora e o papel e tente novamente. Imprimir não dá entrada na mercadoria nem abate a lista.", Alert.AlertType.WARNING);
            }
        });
    }


}
