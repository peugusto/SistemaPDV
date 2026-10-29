package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.DiaPao;
import com.casarural.sistemapdv.model.entities.PrevisaoPao;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.TurnoPao;
import com.casarural.sistemapdv.services.PaoService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Tema;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static com.casarural.sistemapdv.model.entities.enums.TurnoPao.MANHA;
import static com.casarural.sistemapdv.model.entities.enums.TurnoPao.TARDE;

/**
 * "Pão do dia": registra quantos pães chegaram às 7h e às 15h, mostra vendidos e sobra,
 * e sugere quanto comprar na próxima compra. Sem PIN: é usado todo dia por quem está no caixa.
 */
public class PaoController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(PaoController.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DIAS_NA_TELA = 14;

    @FXML private Label lblProduto;
    @FXML private DatePicker dataDia;
    @FXML private Label lblSituacaoManha, lblSituacaoTarde;
    @FXML private TextField txtCompraManha, txtCompraTarde;
    @FXML private Button btnRegistrarManha, btnRegistrarTarde;
    @FXML private Label lblVendidoManha, lblSobraManha, lblSobraManhaRotulo;
    @FXML private Label lblVendidoTarde, lblSobraTarde, lblSobraTardeRotulo;
    @FXML private Label lblSugestaoNumero, lblSugestaoQuando, lblSugestaoExplica, lblSugestaoProxima, lblSugestaoBase;
    @FXML private BarChart<String, Number> graficoDias;
    @FXML private TableView<DiaPao> tabelaDias;
    @FXML private TableColumn<DiaPao, String> colData, colManha, colTarde, colVendidoDia, colSobraDia, colObs;

    private PaoService service;
    private Product pao;
    private List<DiaPao> historico = List.of();
    /** Relógio da tela; os testes trocam para simular outro horário. */
    private java.time.Clock relogio = java.time.Clock.systemDefaultZone();
    private boolean atualizandoHorario;
    private final javafx.animation.Timeline atualizacao = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(30), e -> atualizarHorario()));
    private LocalDateTime agora() { return LocalDateTime.now(relogio); }
    public void setClock(java.time.Clock clock) { relogio = java.util.Objects.requireNonNull(clock); }
    public void recarregar() { if (pao != null) carregar(); }
    public void atualizarHorario() {
        if (pao == null) return;
        atualizandoHorario = true;
        try { mostrarDia(); mostrarSugestao(); mostrarHistorico(); }
        finally { atualizandoHorario = false; }
    }
    private void observarJanela(Window janela) {
        if (janela == null) return;
        janela.showingProperty().addListener((o, a, visivel) -> {
            if (visivel) atualizacao.play(); else atualizacao.stop();
        });
        if (janela.isShowing()) atualizacao.play();
    }

    public static void abrir(Window dono) {
        try {
            FXMLLoader loader = new FXMLLoader(PaoController.class.getResource("/com/casarural/sistemapdv/view/PaoDia.fxml"));
            Parent root = loader.load();
            PaoController controller = loader.getController();
            controller.setService(new PaoService(DaoFactory.createPaoDAO(), DaoFactory.createConfiguracaoDAO(),
                    DaoFactory.createProductDAO()));
            if (controller.pao == null) return;

            Stage stage = new Stage();
            stage.setTitle("Pão do dia - Casa Rural");
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
            log.error("Erro ao abrir Pão do dia", e);
            Alerts.showAlert("Pão do dia", "Não foi possível abrir a tela", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void setService(PaoService service) {
        this.service = service;
        Optional<Product> produto = service.produtoPao();
        if (produto.isEmpty()) {
            Alerts.showAlertAndWait("Pão do dia", "Produto do pão não encontrado",
                    "O relatório usa o produto de código 1 (PÃO FRANCÊS). Cadastre de novo esse produto "
                            + "ou peça ajuda ao responsável pelo sistema.", Alert.AlertType.WARNING);
            return;
        }
        pao = produto.get();
        lblProduto.setText(pao.getNomeProduto() + "  ·  " + Money.format(pao.getPrecoProduto())
                + " a unidade  ·  código " + pao.getCodBarras());
        dataDia.setValue(agora().toLocalDate());
        carregar();
    }

    /** Para os testes: finge que agora é outro horário. */
    public void setAgora(LocalDateTime agora) {
        java.time.ZoneId zona = relogio.getZone();
        setClock(java.time.Clock.fixed(agora.atZone(zona).toInstant(), zona));
        if (pao != null) { carregar(); }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Constraints.setTextFieldInteger(txtCompraManha);
        Constraints.setTextFieldInteger(txtCompraTarde);
        txtCompraManha.setOnAction(e -> onRegistrarManha());
        txtCompraTarde.setOnAction(e -> onRegistrarTarde());
        dataDia.valueProperty().addListener((obs, antes, agora) -> {
            if (agora != null && pao != null) mostrarDia();
        });
        configurarTabela();
        atualizacao.setCycleCount(javafx.animation.Animation.INDEFINITE);
        dataDia.sceneProperty().addListener((o, anterior, cena) -> {
            if (cena == null) { atualizacao.stop(); return; }
            observarJanela(cena.getWindow());
            cena.windowProperty().addListener((obs, antes, janela) -> observarJanela(janela));
            cena.addEventFilter(KeyEvent.KEY_PRESSED, evento -> {
                if (evento.getCode() == KeyCode.F5) { recarregar(); evento.consume(); }
            });
        });
    }

    @FXML public void onRegistrarManha() { registrar(MANHA, txtCompraManha); }
    @FXML public void onRegistrarTarde() { registrar(TARDE, txtCompraTarde); }

    private void registrar(TurnoPao turno, TextField campo) {
        LocalDate dia = dataDia.getValue();
        if (dia == null) return;
        if (dia.isAfter(agora().toLocalDate())) {
            Alerts.showAlert("Pão do dia", null, "Não dá para registrar um dia que ainda não chegou.", Alert.AlertType.WARNING);
            return;
        }
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.isEmpty()) {
            Alerts.showAlert("Pão do dia", null, "Digite quantos pães chegaram.", Alert.AlertType.WARNING);
            campo.requestFocus();
            return;
        }
        try {
            int quantidade = Integer.parseInt(texto);
            service.registrarCompra(pao, dia, turno, quantidade);
            log.info("Compra de pão registrada: {} {} = {}", dia, turno, quantidade);
            carregar();
        } catch (Exception e) {
            log.error("Erro ao registrar compra de pão", e);
            Alerts.showAlert("Pão do dia", "Não foi possível registrar", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void carregar() {
        historico = service.historico(pao, agora().toLocalDate());
        mostrarDia();
        mostrarSugestao();
        mostrarHistorico();
    }

    private DiaPao diaPao(LocalDate data) {
        return historico.stream().filter(d -> d.getData().equals(data)).findFirst().orElse(new DiaPao(data));
    }

    // ============================== CARTÕES DO DIA ==============================

    private void mostrarDia() {
        DiaPao d = diaPao(dataDia.getValue());
        boolean hoje = d.getData().equals(agora().toLocalDate());
        mostrarTurno(d, MANHA, hoje, txtCompraManha, btnRegistrarManha, lblVendidoManha, lblSobraManha, lblSobraManhaRotulo, lblSituacaoManha);
        mostrarTurno(d, TARDE, hoje, txtCompraTarde, btnRegistrarTarde, lblVendidoTarde, lblSobraTarde, lblSobraTardeRotulo, lblSituacaoTarde);
    }

    private void mostrarTurno(DiaPao d, TurnoPao t, boolean hoje, TextField campo, Button botao,
                              Label vendido, Label sobra, Label sobraRotulo, Label situacao) {
        Integer comprado = d.getComprado(t);
        if (!atualizandoHorario) campo.setText(comprado == null ? "" : String.valueOf(comprado));
        botao.setText(comprado == null ? "Registrar" : "Corrigir");
        vendido.setText(String.valueOf(d.getVendido(t)));

        boolean turnoAberto = hoje && agora().toLocalTime().isBefore(t.getFim());
        sobraRotulo.setText(turnoAberto ? "Tem agora" : "Sobrou");
        Integer s = d.getSobra(t);
        sobra.setText(s == null ? "—" : String.valueOf(s));
        Tema.estado(sobra, s == null ? null : (s == 0 ? "texto-perigo" : null));

        situacao.getStyleClass().removeAll("etiqueta-sucesso", "etiqueta-perigo", "etiqueta-alerta", "etiqueta-neutra");
        if (d.acabou(t)) {
            LocalTime ultima = d.getUltimaVenda(t);
            situacao.setText(ultima == null ? "Acabou" : "Acabou às " + ultima.format(HORA));
            situacao.getStyleClass().add("etiqueta-perigo");
        } else if (comprado == null) {
            boolean jaPassou = !hoje || !agora().toLocalTime().isBefore(t.getInicio());
            situacao.setText(jaPassou ? "Falta registrar" : "Ainda não chegou");
            situacao.getStyleClass().add(jaPassou ? "etiqueta-alerta" : "etiqueta-neutra");
        } else {
            situacao.setText(turnoAberto ? "Tem pão" : "Sobrou " + s);
            situacao.getStyleClass().add("etiqueta-sucesso");
        }
    }

    // ============================== SUGESTÃO ==============================

    /**
     * Mostra a próxima compra conforme a hora:
     * antes das 15h sem registro da manhã → manhã de hoje; até a compra da tarde → 15h de hoje;
     * depois → amanhã às 7h.
     */
    private void mostrarSugestao() {
        LocalDate hoje = agora().toLocalDate();
        LocalTime hora = agora().toLocalTime();
        DiaPao diaHoje = diaPao(hoje);
        LocalDate amanha = hoje.plusDays(1);

        // Para amanhã, a manhã de hoje só conta depois que acabou (15h)
        List<DiaPao> paraAmanha = hora.isBefore(MANHA.getFim())
                ? historico.stream().filter(d -> !d.getData().equals(hoje)).collect(Collectors.toList())
                : historico;
        PrevisaoPao.Sugestao amanhaManha = PrevisaoPao.sugerir(paraAmanha, amanha, MANHA);
        String textoAmanha = nomeDia(amanha) + " às 7h";

        if (hora.isBefore(MANHA.getFim()) && diaHoje.getComprado(MANHA) == null) {
            PrevisaoPao.Sugestao s = PrevisaoPao.sugerir(historico, hoje, MANHA);
            mostrarPrincipal(s, s.getProcura(), "hoje às 7h",
                    "A manhã de " + nomeDiaSemana(hoje) + " costuma vender " + s.getProcura() + " pães.", MANHA);
            PrevisaoPao.Sugestao tarde = PrevisaoPao.sugerir(historico, hoje, TARDE);
            lblSugestaoProxima.setText(tarde.temBase()
                    ? "Depois, às 15h: a tarde costuma vender " + tarde.getProcura() + " (desconte a sobra da manhã)."
                    : "");
        } else if (hora.isBefore(TARDE.getFim()) && diaHoje.getComprado(TARDE) == null) {
            PrevisaoPao.Sugestao s = PrevisaoPao.sugerir(historico, hoje, TARDE);
            boolean manhaAberta = hora.isBefore(TARDE.getInicio());
            Integer sobraAgora = diaHoje.getSobra(MANHA);
            Integer sobra = PrevisaoPao.sobraPrevistaAs15h(sobraAgora, diaHoje.getVendido(MANHA),
                    PrevisaoPao.sugerir(historico, hoje, MANHA).getProcura(), !manhaAberta);
            int comprar = PrevisaoPao.comprarNaTarde(s.getProcura(), sobra);
            String explica;
            if (sobra == null) {
                explica = "A tarde costuma vender " + s.getProcura() + " pães. Registre a compra da manhã para descontar o que sobrar.";
            } else if (manhaAberta) {
                explica = "A tarde costuma vender " + s.getProcura() + " pães. Agora tem " + sobraAgora
                        + "; até as 15h devem sobrar uns " + sobra + ", já descontados. Confira de novo às 15h.";
            } else {
                explica = "A tarde costuma vender " + s.getProcura() + " pães; sobraram " + sobra + " da manhã, já descontados.";
            }
            mostrarPrincipal(s, comprar, "hoje às 15h", explica, TARDE);
            lblSugestaoProxima.setText(amanhaManha.temBase() ? textoAmanha + ": " + amanhaManha.getProcura() + " pães." : "");
        } else {
            mostrarPrincipal(amanhaManha, amanhaManha.getProcura(), textoAmanha.toLowerCase(PT_BR),
                    "A manhã de " + nomeDiaSemana(amanha) + " costuma vender " + amanhaManha.getProcura() + " pães.", MANHA);
            lblSugestaoProxima.setText("");
        }
    }

    private void mostrarPrincipal(PrevisaoPao.Sugestao s, int comprar, String quando, String explica, TurnoPao t) {
        if (!s.temBase()) {
            lblSugestaoNumero.setText("—");
            lblSugestaoQuando.setText(quando);
            lblSugestaoExplica.setText("Ainda não há vendas suficientes de pão para sugerir. "
                    + "Registre quantos chegam por alguns dias: a sugestão aparece sozinha.");
            lblSugestaoBase.setText("");
            return;
        }
        lblSugestaoNumero.setText(String.valueOf(comprar));
        lblSugestaoQuando.setText("pães " + quando);
        lblSugestaoExplica.setText(explica);

        String base = s.isMesmoDiaDaSemana()
                ? "Base: média " + (s.getBase().size() == 1 ? "do último " : "das últimas " + s.getBase().size() + " ")
                  + nomeDiaSemanaPlural(s.getBase().get(0).getData(), s.getBase().size())
                : "Base: últimos " + s.getBase().size() + " dias (ainda há poucos dias iguais da semana)";
        long acabou = s.diasQueAcabou(t);
        if (acabou > 0) {
            base += ". Em " + acabou + (acabou == 1 ? " desses dias" : " desses dias") + " o pão acabou antes da hora "
                    + "(foi contado o que teria vendido)";
        }
        lblSugestaoBase.setText(base.endsWith(".") ? base : base + ".");
    }

    // ============================== HISTÓRICO ==============================

    private void configurarTabela() {
        colData.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getData().format(DIA_MES) + " " + c.getValue().getData().getDayOfWeek().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "")));
        colManha.setCellValueFactory(c -> new SimpleStringProperty(chegouVendeu(c.getValue(), MANHA)));
        colTarde.setCellValueFactory(c -> new SimpleStringProperty(chegouVendeu(c.getValue(), TARDE)));
        colVendidoDia.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getVendidoNoDia())));
        colSobraDia.setCellValueFactory(c -> {
            Integer s = c.getValue().getSobra(TARDE);
            return new SimpleStringProperty(s == null ? "—" : String.valueOf(s));
        });
        colObs.setCellValueFactory(c -> new SimpleStringProperty(observacao(c.getValue())));
        tabelaDias.setRowFactory(tv -> {
            TableRow<DiaPao> row = new TableRow<>();
            // Clicar num dia mostra ele nos cartões (para registrar um dia esquecido)
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) dataDia.setValue(row.getItem().getData());
            });
            return row;
        });
    }

    private static String chegouVendeu(DiaPao d, TurnoPao t) {
        Integer comprado = d.getComprado(t);
        return (comprado == null ? "—" : String.valueOf(comprado)) + "  /  " + d.getVendido(t);
    }

    private static String observacao(DiaPao d) {
        List<String> obs = new ArrayList<>();
        for (TurnoPao t : TurnoPao.values()) {
            if (d.acabou(t)) {
                LocalTime ultima = d.getUltimaVenda(t);
                obs.add("acabou " + (ultima == null ? "" : "às " + ultima.format(HORA) + " ")
                        + (t == MANHA ? "de manhã" : "à tarde"));
            }
        }
        if ((d.getVendido(MANHA) > 0 && d.getComprado(MANHA) == null)
                || (d.getVendido(TARDE) > 0 && d.getComprado(TARDE) == null)) {
            obs.add("falta registrar a compra");
        }
        return String.join("; ", obs);
    }

    private void mostrarHistorico() {
        LocalDate hoje = agora().toLocalDate();
        List<DiaPao> ultimos = historico.stream()
                .filter(d -> d.getData().isAfter(hoje.minusDays(DIAS_NA_TELA)))
                .sorted(Comparator.comparing(DiaPao::getData))
                .collect(Collectors.toList());

        XYChart.Series<String, Number> chegaram = new XYChart.Series<>();
        chegaram.setName("Chegaram");
        XYChart.Series<String, Number> vendidos = new XYChart.Series<>();
        vendidos.setName("Vendidos");
        for (DiaPao d : ultimos) {
            String rotulo = d.getData().format(DIA_MES);
            int chegou = valor(d.getComprado(MANHA)) + valor(d.getComprado(TARDE));
            chegaram.getData().add(new XYChart.Data<>(rotulo, chegou));
            vendidos.getData().add(new XYChart.Data<>(rotulo, d.getVendidoNoDia()));
        }
        graficoDias.getData().setAll(List.of(chegaram, vendidos));

        List<DiaPao> tabela = new ArrayList<>(ultimos);
        tabela.sort(Comparator.comparing(DiaPao::getData).reversed());
        tabelaDias.setItems(FXCollections.observableArrayList(tabela));
    }

    // ============================== AJUDA ==============================

    private static int valor(Integer i) { return i == null ? 0 : i; }

    /** "Amanhã (quarta-feira)" */
    private String nomeDia(LocalDate dia) {
        String semana = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        return (dia.equals(agora().toLocalDate().plusDays(1)) ? "Amanhã" : dia.format(DIA_MES)) + " (" + semana + ")";
    }

    /** "terça" */
    private static String nomeDiaSemana(LocalDate dia) {
        return dia.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR).replace("-feira", "");
    }

    /** "terças" / "sábados" */
    private static String nomeDiaSemanaPlural(LocalDate dia, int quantidade) {
        String nome = nomeDiaSemana(dia);
        return quantidade == 1 ? nome : nome + "s";
    }
}
