package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.CashRegisterService;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.ViewLoader;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    @FXML private MenuBar menuPrincipal;
    @FXML private MenuItem menuSair;
    @FXML private MenuItem menuConfiguracoes;

    @FXML private Label labelData;
    @FXML private Label labelHora;

    @FXML private Button botaoPDV;
    @FXML private Button botaoProdutos;
    @FXML private Button botaoConsultarProdutos;
    @FXML private Button botaoClientes;
    @FXML private Button botaoFiados;
    @FXML private Button botaoHistorico;

    // Barra do caixa
    @FXML private Label labelCaixaStatus;
    @FXML private Label labelCaixaGaveta;
    @FXML private Button btnCaixaAbrirFechar;
    @FXML private Button btnRetirar;
    @FXML private Button btnColocar;

    private final CashRegisterService caixaService = new CashRegisterService();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        iniciarRelogio();
        configurarAtalhos();
        atualizarCaixa();
        // Depois que a janela aparecer: avisa se ficou um caixa aberto de outro dia
        Platform.runLater(this::verificarCaixaEsquecido);
    }

    private void iniciarRelogio() {
        DateTimeFormatter formatadorData = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter formatadorHora = DateTimeFormatter.ofPattern("HH:mm:ss");

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime agora = LocalDateTime.now();
            labelData.setText(agora.format(formatadorData));
            labelHora.setText(agora.format(formatadorHora));
        }));

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void configurarAtalhos() {
        botaoPDV.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F1) {
                        onBotaoPDVAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F2) {
                        onButtonProductAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F3) {
                        onButtonProductListAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F4) {
                        onButtonCustomerAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F5) {
                        onButtonCustomerListAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F6) {
                        onCaixaAbrirFecharAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F7) {
                        onMenuPaoAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F8) {
                        onMenuRelatoriosAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F9) {
                        onMenuListaComprasAction();
                        event.consume();
                    }
                });
            }
        });
    }

    // ===================== Caixa =====================

    /** Atualiza a barra do caixa: aberto/fechado e quanto deve ter na gaveta. */
    private void atualizarCaixa() {
        try {
            Optional<CashRegister> aberto = caixaService.findOpen();
            if (aberto.isPresent()) {
                CashRegister caixa = aberto.get();
                labelCaixaStatus.setText("CAIXA ABERTO desde " + DATA_HORA.format(caixa.getAbertoEm()));
                trocarEtiqueta(labelCaixaStatus, "etiqueta-sucesso");
                labelCaixaGaveta.setText("Dinheiro na gaveta: " + Money.format(caixaService.summarize(caixa).getDinheiroEsperado()));
                btnCaixaAbrirFechar.setText("Fechar caixa (F6)");
            } else {
                labelCaixaStatus.setText("CAIXA FECHADO");
                trocarEtiqueta(labelCaixaStatus, "etiqueta-perigo");
                labelCaixaGaveta.setText("Abra o caixa para começar a vender");
                btnCaixaAbrirFechar.setText("Abrir caixa (F6)");
            }
            btnRetirar.setDisable(aberto.isEmpty());
            btnColocar.setDisable(aberto.isEmpty());
        } catch (DbException e) {
            labelCaixaStatus.setText("CAIXA: SEM CONEXÃO COM O BANCO");
            trocarEtiqueta(labelCaixaStatus, "etiqueta-alerta");
            labelCaixaGaveta.setText("");
        }
    }

    /** Troca a cor da etiqueta (classes do componentes.css) mantendo o formato. */
    private static void trocarEtiqueta(Label etiqueta, String cor) {
        etiqueta.getStyleClass().removeAll("etiqueta-sucesso", "etiqueta-perigo", "etiqueta-alerta");
        etiqueta.getStyleClass().add(cor);
    }

    private void verificarCaixaEsquecido() {
        try {
            caixaService.findOpenFromPreviousDay().ifPresent(caixa -> {
                boolean fechar = Alerts.showConfirmation("Caixa de outro dia",
                        "O caixa aberto em " + DATA_HORA.format(caixa.getAbertoEm()) + " não foi fechado.",
                        "Feche esse caixa antes de começar o dia, para as contas não se misturarem.\n\nFechar agora?");
                if (fechar) {
                    abrirTelaFechamento(caixa);
                }
            });
        } catch (DbException e) {
            // sem conexão: a barra do caixa já mostra o problema
        }
    }

    /**
     * Garante um caixa aberto e de hoje antes de vender. Retorna false se o usuário desistir.
     */
    private boolean garantirCaixaAberto() {
        Optional<CashRegister> antigo = caixaService.findOpenFromPreviousDay();
        if (antigo.isPresent()) {
            Alerts.showAlertAndWait("Caixa de outro dia",
                    "O caixa aberto em " + DATA_HORA.format(antigo.get().getAbertoEm()) + " ainda não foi fechado.",
                    "Feche esse caixa primeiro. Depois abra o caixa de hoje.", Alert.AlertType.WARNING);
            abrirTelaFechamento(antigo.get());
            if (caixaService.findOpen().isPresent()) return false;   // desistiu de fechar
        }
        if (caixaService.findOpen().isPresent()) return true;
        return abrirTelaValor(CaixaValorController.Modo.ABRIR, null);
    }

    @FXML
    public void onCaixaAbrirFecharAction() {
        try {
            Optional<CashRegister> aberto = caixaService.findOpen();
            if (aberto.isPresent()) {
                abrirTelaFechamento(aberto.get());
            } else {
                abrirTelaValor(CaixaValorController.Modo.ABRIR, null);
            }
        } catch (DbException e) {
            Alerts.showAlert("Caixa", null, e.getMessage(), Alert.AlertType.WARNING);
        }
        atualizarCaixa();
    }

    @FXML
    public void onRetirarAction() {
        caixaService.findOpen().ifPresent(caixa -> abrirTelaValor(CaixaValorController.Modo.SANGRIA, caixa));
        atualizarCaixa();
    }

    @FXML
    public void onColocarAction() {
        caixaService.findOpen().ifPresent(caixa -> abrirTelaValor(CaixaValorController.Modo.REFORCO, caixa));
        atualizarCaixa();
    }

    @FXML
    public void onHistoricoCaixaAction() {
        ViewLoader.showView(false, "/com/casarural/sistemapdv/view/HistoricoCaixa.fxml", "Histórico de caixas",
                (HistoricoCaixaController controller) -> controller.configurar(caixaService));
    }

    /** Abre a tela de valor (abrir, retirar ou colocar). Retorna true se o usuário confirmou. */
    private boolean abrirTelaValor(CaixaValorController.Modo modo, CashRegister caixa) {
        CaixaValorController[] tela = new CaixaValorController[1];
        String titulo = modo == CaixaValorController.Modo.ABRIR ? "Abrir caixa"
                : modo == CaixaValorController.Modo.SANGRIA ? "Retirar dinheiro" : "Colocar dinheiro";
        ViewLoader.showView(false, "/com/casarural/sistemapdv/view/CaixaValor.fxml", titulo,
                (CaixaValorController controller) -> {
                    controller.configurar(modo, caixaService, caixa);
                    tela[0] = controller;
                });
        // showView só retorna depois que a janela fecha
        atualizarCaixa();
        return tela[0] != null && tela[0].isConcluido();
    }

    private void abrirTelaFechamento(CashRegister caixa) {
        ViewLoader.showView(false, "/com/casarural/sistemapdv/view/FecharCaixa.fxml", "Fechar caixa",
                (FecharCaixaController controller) -> controller.configurar(caixaService, caixa));
        atualizarCaixa();
    }

    // ===================== Telas =====================

    @FXML
    public void onButtonProductAction() {
        ViewLoader.showView(false, "/com/casarural/sistemapdv/view/CadastroProduto.fxml", "Cadastro de Produto",
                (CadastroProdutosController controller) -> {
                    controller.setProductService(new ProductService(DaoFactory.createProductDAO()));
                    controller.setProduct(new Product());
                }
        );
    }

    @FXML
    public void onButtonCustomerAction() {
        ViewLoader.showView(false, "/com/casarural/sistemapdv/view/RegisterCustomer.fxml", "Cadastro de Cliente",
                (RegisterCustomerController controller) -> {
                    controller.setCustomerService(new CustomerService(DaoFactory.createCustomerDAO()));
                    controller.setCustomer(new Customer());
                }
        );
    }

    @FXML
    public void onButtonCustomerListAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/customer_list.fxml", "Lista de Cliente",
                (CustomerListController controller) -> {
                    controller.setCustomerService(new CustomerService(DaoFactory.createCustomerDAO()));
                    controller.updateTableView();
                }
        );
        atualizarCaixa();   // recebimentos de fiado em dinheiro mudam a gaveta
    }

    @FXML
    public void onButtonProductListAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/product_list.fxml", "Lista de Produtos",
                (ProductListController controller) -> {
                    controller.setProductService(new ProductService(DaoFactory.createProductDAO()));
                    controller.updateTableView();
                }
        );
    }

    @FXML
    public void onBotaoPDVAction() {
        try {
            if (!garantirCaixaAberto()) return;
        } catch (DbException e) {
            Alerts.showAlert("Caixa", null, e.getMessage(), Alert.AlertType.WARNING);
            return;
        }
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/pdv.fxml", "Caixa Aberto",
                (PDVController controller) -> {
                    controller.setProductService(new ProductService(DaoFactory.createProductDAO()));
                }
        );
        atualizarCaixa();
    }

    /** Ajuda > Manual. */
    @FXML
    public void onMenuManualAction() {
        ManualController.abrir(botaoPDV.getScene().getWindow(), null);
    }

    /** Ajuda > Problemas e soluções: o manual já aberto nesse assunto. */
    @FXML
    public void onMenuProblemasAction() {
        ManualController.abrir(botaoPDV.getScene().getWindow(), ManualController.PROBLEMAS);
    }

    /** Ajuda > Sobre. */
    @FXML
    public void onMenuSobreAction() {
        SobreController.abrir(botaoPDV.getScene().getWindow());
    }

    /** Relatórios > Pão do dia (F7): sem PIN, usado todo dia na compra do pão. */
    @FXML
    public void onMenuPaoAction() {
        PaoController.abrir(botaoPDV.getScene().getWindow());
    }

    /** Relatórios > Lista de compras (F9): sem PIN, só quantidades. */
    @FXML
    public void onMenuListaComprasAction() {
        ListaComprasController.abrir(botaoPDV.getScene().getWindow());
    }

    /** Relatórios > Vendas e produtos (F8): pede o PIN, mostra faturamento. */
    @FXML
    public void onMenuRelatoriosAction() {
        RelatoriosController.abrir(botaoPDV.getScene().getWindow());
    }

    @FXML
    public void onBotaoHistoricoAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/order-list.fxml", "Historico de Vendas"
        );
    }
}
