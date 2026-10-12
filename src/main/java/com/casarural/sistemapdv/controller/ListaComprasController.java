package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.Periodo;
import com.casarural.sistemapdv.model.entities.RegraListaCompras;
import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import com.casarural.sistemapdv.services.RelatorioService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/** Relatórios > Lista de compras (F9). Sem PIN: só quantidades. */
public class ListaComprasController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ListaComprasController.class);
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    @FXML private Label lblPeriodo, lblQtdProdutos, lblAcabaram, lblUnidades, lblCopiado, lblCusto, lblCustoDetalhe;
    @FXML private TableView<Item> tabela;
    @FXML private TableColumn<Item, Item> colComprar;
    @FXML private TableColumn<Item, String> colProduto, colVendeu, colTem, colSituacao, colCustoItem;

    private RelatorioService service;
    private LocalDate hoje = LocalDate.now();

    public static void abrir(Window dono) {
        try {
            FXMLLoader loader = new FXMLLoader(ListaComprasController.class.getResource("/com/casarural/sistemapdv/view/ListaCompras.fxml"));
            Parent root = loader.load();
            ListaComprasController controller = loader.getController();
            controller.setService(new RelatorioService(DaoFactory.createRelatorioDAO()));

            Stage stage = new Stage();
            stage.setTitle("Lista de compras - Casa Rural");
            if (dono != null) stage.initOwner(dono);
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            Tema.aplicar(scene);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) stage.close();
            });
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Erro ao abrir a lista de compras", e);
            Alerts.showAlert("Lista de compras", "Não foi possível abrir a lista", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void setService(RelatorioService service) {
        this.service = service;
        carregar();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Quantidade sempre editável: campo dentro da célula
        colComprar.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colComprar.setCellFactory(col -> new TableCell<>() {
            private final TextField campo = new TextField();
            {
                campo.setPrefWidth(80);
                Constraints.setTextFieldInteger(campo);
                campo.textProperty().addListener((obs, antes, agora) -> {
                    Item item = getItem();
                    if (item == null) return;
                    item.setComprar(agora == null || agora.isBlank() ? 0 : Integer.parseInt(agora));
                    atualizarResumo();
                });
            }
            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    campo.setText(String.valueOf(item.getComprar()));
                    setGraphic(campo);
                }
            }
        });
        colProduto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduto().getNomeProduto()));
        colVendeu.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getVendido())));
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
        hoje = LocalDate.now();
        Periodo semana = Periodo.ultimosDias(hoje, RegraListaCompras.DIAS);
        lblPeriodo.setText("Vendas de " + semana.getDe().format(DIA_MES) + " a " + semana.getAte().format(DIA_MES)
                + " (últimos " + RegraListaCompras.DIAS + " dias)");
        try {
            List<Item> itens = RegraListaCompras.montar(service.porProduto(semana));
            tabela.setItems(FXCollections.observableArrayList(itens));
            lblCopiado.setText("");
            atualizarResumo();
        } catch (RuntimeException e) {
            log.error("Erro ao montar a lista de compras", e);
            Alerts.showAlert("Lista de compras", "Não foi possível montar a lista", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void atualizarResumo() {
        List<Item> itens = tabela.getItems();
        long produtos = itens.stream().filter(i -> i.getComprar() > 0).count();
        lblQtdProdutos.setText(String.valueOf(produtos));
        long acabaram = itens.stream().filter(Item::acabou).count();
        lblAcabaram.setText(String.valueOf(acabaram));
        Tema.estado(lblAcabaram, acabaram > 0 ? "texto-perigo" : null);
        lblUnidades.setText(String.valueOf(itens.stream().mapToInt(Item::getComprar).sum()));

        // Custo estimado: comprar x último custo; avisa quantos produtos estão sem custo
        java.math.BigDecimal total = itens.stream().map(Item::getCustoEstimado).filter(java.util.Objects::nonNull)
                .reduce(com.casarural.sistemapdv.util.Money.ZERO, java.math.BigDecimal::add);
        long semCusto = itens.stream().filter(i -> i.getComprar() > 0 && i.getProduto().getPrecoCusto() == null).count();
        boolean algumCusto = itens.stream().anyMatch(i -> i.getComprar() > 0 && i.getProduto().getPrecoCusto() != null);
        lblCusto.setText(algumCusto ? com.casarural.sistemapdv.util.Money.format(total) : "—");
        lblCustoDetalhe.setText(semCusto == 0 ? "pelo último custo pago"
                : semCusto + (semCusto == 1 ? " produto sem custo" : " produtos sem custo") + " (não somados)");
    }

    @FXML
    public void onAtualizar() {
        carregar();
    }

    /** Copia a lista em texto (para WhatsApp, bloco de notas; depois, impressora). */
    @FXML
    public void onCopiar() {
        String texto = RegraListaCompras.texto(tabela.getItems(), hoje);
        ClipboardContent conteudo = new ClipboardContent();
        conteudo.putString(texto);
        Clipboard.getSystemClipboard().setContent(conteudo);
        lblCopiado.setText("Lista copiada. Cole no WhatsApp ou no bloco de notas.");
    }
}
