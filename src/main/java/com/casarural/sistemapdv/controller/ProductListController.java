package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.AjusteEstoque;
import com.casarural.sistemapdv.model.entities.RegistroAjusteEstoque;
import com.casarural.sistemapdv.services.AjusteEstoqueService;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Tema;
import com.casarural.sistemapdv.util.MoneyCells;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ProductListController implements Initializable {
    private ProtecaoPin protecao = new ProtecaoPin();

    private static final Logger log = LoggerFactory.getLogger(ProductListController.class);


    private ProductService service;
    private AjusteEstoqueService ajustes=new AjusteEstoqueService();
    @FXML private Button botaoPerda,botaoContagem,botaoHistoricoAjustes;

    @FXML private TextField searchField;
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer> idColumn;
    @FXML private TableColumn<Product, String> barcodeColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, BigDecimal> priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
    @FXML private TableColumn<Product, Boolean> fiadoColumn;

    /** A partir deste número (inclusive) o estoque aparece em vermelho. */
    private static final int ESTOQUE_BAIXO = 15;
    @FXML private TableColumn<Product, Void> actionsColumn;

    private final ObservableList<Product> obsList=FXCollections.observableArrayList();
    private final FilteredList<Product> filtrados=new FilteredList<>(obsList,p->true);


    public void setProductService(ProductService service) {
        this.service = service;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {
        productTable.setItems(filtrados);
        searchField.textProperty().addListener((o,a,n)->filtrados.setPredicate(p->{
            String busca=n==null?"":n.toLowerCase(java.util.Locale.ROOT);
            return p.getNomeProduto().toLowerCase(java.util.Locale.ROOT).contains(busca)||p.getCodBarras().toLowerCase(java.util.Locale.ROOT).contains(busca);
        }));
        productTable.getSelectionModel().selectedItemProperty().addListener((o,a,n)->atualizarAcoesEstoque());
        initEditButtons();atualizarAcoesEstoque();

        idColumn.setCellValueFactory(new PropertyValueFactory<>("idProduto"));
        barcodeColumn.setCellValueFactory(new PropertyValueFactory<>("codBarras"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("nomeProduto"));
        // Produto desativado aparece apagado, com "(inativo)"
        nameColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String nome, boolean empty) {
                super.updateItem(nome, empty);
                Product p = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || nome == null || p == null) {
                    setText(null);
                    Tema.estado(this, null);
                } else {
                    setText(p.isAtivo() ? nome : nome + "  (inativo)");
                    Tema.estado(this, p.isAtivo() ? null : "texto-suave");
                }
            }
        });
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("precoProduto"));
        MoneyCells.format(priceColumn);
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("estoque"));
        // Vermelho só na célula do produto com estoque baixo (antes a coluna inteira ficava vermelha)
        stockColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer estoque, boolean empty) {
                super.updateItem(estoque, empty);
                Product produto = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || estoque == null || produto == null) {
                    setText(null);
                    Tema.estado(this, null);
                } else if (!produto.isControlaEstoque()) {
                    // Pesável / sem controle: o número não significa nada, então não aparece
                    setText(produto.isPesavel() ? "Pesável" : "Não controla");
                    Tema.estado(this, "texto-suave");
                } else {
                    setText(String.valueOf(estoque));
                    Tema.estado(this, estoque <= ESTOQUE_BAIXO ? "texto-perigo" : null);
                }
            }
        });
        fiadoColumn.setCellValueFactory(new PropertyValueFactory<>("permiteFiado"));
        fiadoColumn.setCellFactory(col -> new TableCell<>() {
            private final Label etiqueta = new Label("Sem fiado");
            { etiqueta.getStyleClass().addAll("etiqueta", "etiqueta-perigo"); }
            @Override
            protected void updateItem(Boolean permite, boolean empty) {
                super.updateItem(permite, empty);
                setGraphic(null);
                setText(null);
                if (empty || permite == null) return;
                if (permite) {
                    setText("Sim");
                } else {
                    setGraphic(etiqueta);
                }
            }
        });

    }

    public void updateTableView() {
        if (service == null) {
            throw new IllegalStateException("Service was null");
        }
        List<Product> list = service.findAll();
        obsList.setAll(list);atualizarAcoesEstoque();
    }

    private void atualizarAcoesEstoque(){
        Product p=productTable.getSelectionModel().getSelectedItem();boolean controla=p!=null&&p.isControlaEstoque();
        botaoPerda.setDisable(!controla||p.getEstoque()<=0);botaoContagem.setDisable(!controla);botaoHistoricoAjustes.setDisable(p==null);
    }
    @FXML public void onRegistrarPerda(){abrirAjuste(AjusteEstoque.Tipo.PERDA);}
    @FXML public void onCorrigirContagem(){abrirAjuste(AjusteEstoque.Tipo.CONTAGEM);}
    private void abrirAjuste(AjusteEstoque.Tipo tipo){
        Product p=productTable.getSelectionModel().getSelectedItem();if(p==null||!p.isControlaEstoque()||tipo==AjusteEstoque.Tipo.PERDA&&p.getEstoque()<=0)return;
        try{
            FXMLLoader l=new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/AjusteEstoque.fxml"));
            javafx.scene.Parent raiz=l.load();AjusteEstoqueController c=l.getController();c.configurar(p,tipo,ajustes);
            Stage s=new Stage();s.setTitle(tipo==AjusteEstoque.Tipo.PERDA?"Registrar perda":"Corrigir contagem");s.initOwner(productTable.getScene().getWindow());s.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            javafx.scene.Scene cena=new javafx.scene.Scene(raiz);Tema.aplicar(cena);s.setScene(cena);s.showAndWait();
            try{updateTableView();}catch(RuntimeException e){Alerts.showAlert(c.isConcluido()?"Ajuste salvo":"Atualizar estoque",null,"Reabra o estoque para conferir os dados atuais. Um ajuste já confirmado não precisa ser registrado novamente.",Alert.AlertType.WARNING);}
        }catch(java.io.IOException|RuntimeException e){log.error("Falha ao abrir ajuste do produto #{}",p.getIdProduto(),e);Alerts.showAlert("Ajustar estoque",null,"Não foi possível abrir a tela de ajuste.",Alert.AlertType.WARNING);}
    }
    @FXML public void onHistoricoAjustes(){
        Product p=productTable.getSelectionModel().getSelectedItem();if(p==null)return;
        try{
            TableView<RegistroAjusteEstoque> tabela=new TableView<>();tabela.setId("historicoAjustes");tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            colunaHistorico(tabela,"Data",r->r.data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            colunaHistorico(tabela,"Tipo",r->r.ajuste.tipo.toString());colunaHistorico(tabela,"Antes",r->String.valueOf(r.antes));
            colunaHistorico(tabela,"Variação",r->(r.depois-r.antes>0?"+":"")+(r.depois-r.antes));colunaHistorico(tabela,"Depois",r->String.valueOf(r.depois));colunaHistorico(tabela,"Motivo",r->r.ajuste.motivo);
            tabela.setPlaceholder(new Label("Nenhum ajuste registrado para este produto."));
            Button atualizar=new Button("Atualizar"),fechar=new Button("Fechar");
            Runnable carregar=()->{try{tabela.setItems(FXCollections.observableArrayList(ajustes.historico(p.getIdProduto())));}catch(RuntimeException e){Alerts.showAlert("Histórico de ajustes",null,e.getMessage(),Alert.AlertType.WARNING);}};
            Stage s=new Stage();atualizar.setOnAction(e->carregar.run());fechar.setOnAction(e->s.close());
            javafx.scene.layout.VBox raiz=new javafx.scene.layout.VBox(12,new Label("Histórico de "+p.getNomeProduto()+" · "+p.getCodBarras()),new Label("Últimos 200 ajustes. Entradas e vendas mantêm seus registros próprios."),tabela,new HBox(10,atualizar,fechar));
            raiz.setPadding(new javafx.geometry.Insets(20));javafx.scene.layout.VBox.setVgrow(tabela,javafx.scene.layout.Priority.ALWAYS);
            s.setTitle("Histórico de ajustes de estoque");s.initOwner(productTable.getScene().getWindow());s.initModality(javafx.stage.Modality.APPLICATION_MODAL);javafx.scene.Scene cena=new javafx.scene.Scene(raiz,1020,480);Tema.aplicar(cena);s.setScene(cena);carregar.run();s.showAndWait();
        }catch(RuntimeException e){Alerts.showAlert("Histórico de ajustes",null,e.getMessage(),Alert.AlertType.WARNING);}
    }
    private static void colunaHistorico(TableView<RegistroAjusteEstoque> tabela,String nome,java.util.function.Function<RegistroAjusteEstoque,String> valor){
        TableColumn<RegistroAjusteEstoque,String> coluna=new TableColumn<>(nome);coluna.setCellValueFactory(c->new javafx.beans.property.SimpleStringProperty(valor.apply(c.getValue())));
        coluna.setPrefWidth(nome.equals("Motivo")?350:nome.equals("Data")?180:nome.equals("Tipo")?170:75);
        coluna.setCellFactory(c->new TableCell<>(){protected void updateItem(String texto,boolean empty){super.updateItem(texto,empty);setText(empty?null:texto);setTooltip(empty||texto==null?null:new Tooltip(texto));setWrapText(nome.equals("Motivo"));}});
        tabela.getColumns().add(coluna);
    }

    private void initEditButtons() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDelete = new Button("Excluir");
            private final HBox pane = new HBox(10, btnEdit, btnDelete);

            {
                pane.setAlignment(Pos.CENTER_LEFT);

                btnEdit.setOnAction(event -> {
                    Product obj = getTableView().getItems().get(getIndex());
                    onEditAction(obj);
                });

                btnDelete.setOnAction(event -> {
                    Product obj = getTableView().getItems().get(getIndex());
                    onDeleteAction(obj);
                });

                btnEdit.getStyleClass().setAll("button", "botao-pequeno", "botao-info");
                btnDelete.getStyleClass().setAll("button", "botao-pequeno", "botao-perigo");

            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
    }

    private void onEditAction(Product obj) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/ProductForm.fxml"));
            javafx.scene.Parent parent = loader.load();

            ProductFormController controller = loader.getController();
            controller.setProduct(obj);
            controller.setServices(this.service, this);
            controller.updateFormData();

            Stage stage = new Stage();
            stage.setTitle("Editar Produto - " + obj.getNomeProduto());
            javafx.scene.Scene scene = new javafx.scene.Scene(parent);
            Tema.aplicar(scene);
            stage.setScene(scene);
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (java.io.IOException e) {
            log.error("Erro ao carregar o formulário de edição do produto {}", obj.getIdProduto(), e);
            Alerts.showAlert("Erro", "Erro ao carregar o formulário de edição", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Produto sem histórico: exclui. Já vendido: não pode ser excluído (o histórico precisa dele),
     * então oferece desativar.
     */
    private void onDeleteAction(Product obj) {
        try {
            if (service.temHistorico(obj.getIdProduto())) {
                if (!obj.isAtivo()) {
                    Alerts.showAlert("Excluir produto", obj.getNomeProduto(),
                            "Este produto possui histórico e já está desativado. Ele continua disponível para consulta.",
                            Alert.AlertType.INFORMATION);
                    return;
                }
                int escolha = Alerts.showChoice("Excluir produto", obj.getNomeProduto() + " possui histórico",
                        "Ele não pode ser excluído, porque há registros de vendas ou ajustes de estoque vinculados a ele.\n\n"
                                + "Desativado, ele some da venda, da busca e da lista de compras, mas as vendas antigas continuam.\n"
                                + "Para voltar a vender, é só marcar \"Produto ativo\" em Editar.",
                        Alert.AlertType.WARNING, "Voltar", "Desativar produto");
                if (escolha == 0) {
                    if (!protecao.autorizar(productTable.getScene().getWindow(),"desativar o produto "+obj.getNomeProduto())) return;
                    service.desativar(obj);
                    log.info("Produto {} ({}) desativado", obj.getIdProduto(), obj.getNomeProduto());
                    try {
                        updateTableView();
                    } catch (RuntimeException e) {
                        log.error("Produto {} desativado, mas a lista não pôde ser atualizada", obj.getIdProduto(), e);
                        productTable.refresh();
                        Alerts.showAlert("Produto desativado", "O produto foi desativado normalmente",
                                "Não foi possível atualizar a lista. Reabra o estoque para conferir. Não é preciso desativar novamente.",
                                Alert.AlertType.WARNING);
                    }
                }
                return;
            }
            if (Alerts.showConfirmation("Excluir produto", "Excluir " + obj.getNomeProduto() + "?",
                    "Ele nunca foi vendido. Excluir apaga o cadastro de vez.")) {
                if (!protecao.autorizar(productTable.getScene().getWindow(),"excluir definitivamente o produto "+obj.getNomeProduto())) return;
                service.delete(obj.getIdProduto());
                log.info("Produto {} ({}) excluído", obj.getIdProduto(), obj.getNomeProduto());
                updateTableView();
            }
        } catch (ResultadoTransacaoIncertoException e) {
            log.error("Resultado da desativação do produto {} precisa de conferência", obj.getIdProduto(), e);
            Alerts.showAlert("Conferir produto", "Confira a situação de " + obj.getNomeProduto(),
                    "Não foi possível confirmar se o produto foi desativado. Reabra o estoque para conferir a situação atual.",
                    Alert.AlertType.WARNING);
        } catch (DbException e) {
            Alerts.showAlert("Excluir produto", "Não foi possível excluir", e.getMessage(), Alert.AlertType.WARNING);
        }
    }
}
