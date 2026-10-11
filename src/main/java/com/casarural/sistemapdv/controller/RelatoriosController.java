package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.Periodo;
import com.casarural.sistemapdv.model.entities.PontoVenda;
import com.casarural.sistemapdv.model.entities.ResumoVendas;
import com.casarural.sistemapdv.model.entities.VendaProduto;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.AcessoRelatoriosService;
import com.casarural.sistemapdv.services.BuscaProduto;
import com.casarural.sistemapdv.services.RelatorioService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Tema;
import javafx.beans.property.SimpleObjectProperty;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Dashboards de vendas e de produtos. Só abre depois do PIN (faturamento é sensível). */
public class RelatoriosController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(RelatoriosController.class);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final int TOP = 10;

    private static final String TIPO_TODOS = "Todos os tipos";
    private static final String TIPO_PESAVEIS = "Pesáveis";
    private static final String TIPO_SEM_ESTOQUE = "Sem controle de estoque";
    private static final String TIPO_SEM_FIADO = "Sem fiado";

    private static final String ORDEM_QTD = "Mais vendidos (qtd)";
    private static final String ORDEM_VALOR = "Maior faturamento";
    private static final String ORDEM_VENDAS = "Em mais vendas";

    @FXML private Label lblPeriodo;
    @FXML private ToggleGroup grupoPeriodo;
    @FXML private ToggleButton btnHoje, btn7Dias, btn30Dias, btnEsteMes, btnMesPassado;
    @FXML private DatePicker dataDe, dataAte;

    @FXML private Label lblFaturamento, lblFaturamentoDetalhe, lblVendas, lblVendasDetalhe, lblTicket,
            lblFiado, lblFiadoDetalhe, lblRecebido;
    @FXML private BarChart<String, Number> graficoDias, graficoFormas, graficoHoras, graficoSemana;

    @FXML private Label lblProdVenderam, lblProdVenderamDetalhe, lblItens, lblParados, lblCampeao, lblCampeaoDetalhe;
    @FXML private TextField txtBuscaProduto;
    @FXML private ToggleGroup grupoMostrar;
    @FXML private ToggleButton btnQueVenderam, btnParados, btnTodosProdutos;
    @FXML private ComboBox<String> comboTipo, comboOrdem;
    @FXML private TableView<VendaProduto> tabelaProdutos;
    @FXML private TableColumn<VendaProduto, String> colPosicao, colProduto, colQtd, colNumVendas, colValor,
            colParticipacao, colUltima;
    @FXML private BarChart<Number, String> graficoTop;

    private RelatorioService service;
    private AcessoRelatoriosService acesso;
    private Periodo periodo;
    private List<VendaProduto> produtosDoPeriodo = List.of();
    private BigDecimal totalProdutos = Money.ZERO;

    /** Pede o PIN e, se certo, abre os relatórios. */
    public static void abrir(Window dono) {
        AcessoRelatoriosService acesso = new AcessoRelatoriosService(DaoFactory.createConfiguracaoDAO());
        try {
            if (!PinDialogo.liberar(acesso, dono)) return;

            FXMLLoader loader = new FXMLLoader(RelatoriosController.class.getResource("/com/casarural/sistemapdv/view/Relatorios.fxml"));
            Parent root = loader.load();
            RelatoriosController controller = loader.getController();
            controller.setServices(new RelatorioService(DaoFactory.createRelatorioDAO()), acesso);

            Stage stage = new Stage();
            stage.setTitle("Relatórios - Casa Rural");
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
            log.error("Erro ao abrir os relatórios", e);
            Alerts.showAlert("Relatórios", "Não foi possível abrir os relatórios", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void setServices(RelatorioService service, AcessoRelatoriosService acesso) {
        this.service = service;
        this.acesso = acesso;
        usarPeriodo(Periodo.ultimosDias(LocalDate.now(), 30));
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Período
        grupoPeriodo.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) return; // "Ver" com datas desmarca as pílulas
            LocalDate hoje = LocalDate.now();
            if (agora == btnHoje) usarPeriodo(Periodo.hoje(hoje));
            else if (agora == btn7Dias) usarPeriodo(Periodo.ultimosDias(hoje, 7));
            else if (agora == btn30Dias) usarPeriodo(Periodo.ultimosDias(hoje, 30));
            else if (agora == btnEsteMes) usarPeriodo(Periodo.esteMes(hoje));
            else if (agora == btnMesPassado) usarPeriodo(Periodo.mesPassado(hoje));
        });

        // Produtos
        comboTipo.setItems(FXCollections.observableArrayList(TIPO_TODOS, TIPO_PESAVEIS, TIPO_SEM_ESTOQUE, TIPO_SEM_FIADO));
        comboTipo.setValue(TIPO_TODOS);
        comboOrdem.setItems(FXCollections.observableArrayList(ORDEM_QTD, ORDEM_VALOR, ORDEM_VENDAS));
        comboOrdem.setValue(ORDEM_QTD);
        comboTipo.setOnAction(e -> mostrarProdutos());
        comboOrdem.setOnAction(e -> mostrarProdutos());
        txtBuscaProduto.textProperty().addListener((obs, a, b) -> mostrarProdutos());
        grupoMostrar.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) antes.setSelected(true);
            else mostrarProdutos();
        });
        configurarTabelaProdutos();
    }

    @FXML
    public void onVerDatas() {
        try {
            Periodo p = new Periodo(dataDe.getValue(), dataAte.getValue());
            grupoPeriodo.selectToggle(null);
            usarPeriodo(p);
        } catch (IllegalArgumentException e) {
            Alerts.showAlert("Período", null, e.getMessage(), Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void onTrocarPin() {
        if (PinDialogo.criarPin(acesso, lblPeriodo.getScene().getWindow())) {
            Alerts.showAlert("PIN", null, "PIN dos relatórios trocado.", Alert.AlertType.INFORMATION);
        }
    }

    private void usarPeriodo(Periodo p) {
        this.periodo = p;
        dataDe.setValue(p.getDe());
        dataAte.setValue(p.getAte());
        lblPeriodo.setText(p.descricao() + "  ·  " + p.quantidadeDeDias() + (p.umDiaSo() ? " dia" : " dias"));
        try {
            carregarVendas();
            produtosDoPeriodo = service.porProduto(p);
            mostrarProdutos();
        } catch (Exception e) {
            log.error("Erro ao carregar relatórios de {}", p.descricao(), e);
            Alerts.showAlert("Relatórios", "Não foi possível carregar os números", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ============================== VENDAS ==============================

    private void carregarVendas() {
        ResumoVendas r = service.resumo(periodo);
        long dias = periodo.quantidadeDeDias();

        lblFaturamento.setText(Money.format(r.getFaturamento()));
        lblFaturamentoDetalhe.setText(dias > 1
                ? "média de " + Money.format(dividir(r.getFaturamento(), dias)) + " por dia" : "");
        lblVendas.setText(String.valueOf(r.getQuantidadeVendas()));
        lblVendasDetalhe.setText(dias > 1
                ? String.format("média de %.1f por dia", (double) r.getQuantidadeVendas() / dias).replace('.', ',') : "");
        lblTicket.setText(Money.format(r.getTicketMedio()));
        lblFiado.setText(Money.format(r.getFiadoLancado()));
        lblFiadoDetalhe.setText(percentual(r.getFiadoLancado(), r.getFaturamento()) + " do faturamento");
        lblRecebido.setText(Money.format(r.getRecebidoDeFiado()));

        // Um dia só: o gráfico "por dia" teria uma barra; mostra por hora
        Map<LocalDate, PontoVenda> porDia = service.porDia(periodo);
        List<PontoVenda> horas = service.porHora(periodo);
        if (periodo.umDiaSo()) {
            graficoDias.setTitle("Faturamento por hora");
            preencher(graficoDias, horas, true);
        } else if (periodo.quantidadeDeDias() > RelatorioService.MAXIMO_DIAS_NO_GRAFICO) {
            graficoDias.setTitle("Faturamento por mês");
            preencher(graficoDias, RelatorioService.porMes(porDia, periodo), true);
        } else {
            graficoDias.setTitle("Faturamento por dia");
            preencher(graficoDias, RelatorioService.completarDias(porDia, periodo), true);
        }

        List<PontoVenda> formas = new ArrayList<>();
        for (PaymentMethod forma : List.of(PaymentMethod.DINHEIRO, PaymentMethod.PIX, PaymentMethod.CARTAO, PaymentMethod.FIADO)) {
            formas.add(new PontoVenda(nomeForma(forma), 0, r.getPorForma(forma)));
        }
        if (Money.isPositive(r.getOutrasFormas())) {
            formas.add(new PontoVenda("Outros", 0, r.getOutrasFormas()));
        }
        preencher(graficoFormas, formas, true);
        preencher(graficoHoras, horas, false);
        preencher(graficoSemana, RelatorioService.mediaPorDiaDaSemana(porDia, periodo), true);
    }

    /** Põe os pontos no gráfico; emValor = altura em R$, senão em número de vendas. Passar o mouse mostra os dois. */
    private void preencher(BarChart<String, Number> grafico, List<PontoVenda> pontos, boolean emValor) {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (PontoVenda p : pontos) {
            Number altura = emValor ? p.getValor().doubleValue() : p.getVendas();
            XYChart.Data<String, Number> d = new XYChart.Data<>(p.getRotulo(), altura);
            String dica = p.getRotulo() + "\n" + Money.format(p.getValor())
                    + (p.getVendas() > 0 ? "\n" + p.getVendas() + (p.getVendas() == 1 ? " venda" : " vendas") : "");
            d.nodeProperty().addListener((obs, antes, no) -> {
                if (no != null) Tooltip.install(no, new Tooltip(dica));
            });
            serie.getData().add(d);
        }
        grafico.getData().setAll(List.of(serie));
    }

    private static String nomeForma(PaymentMethod forma) {
        return switch (forma) {
            case DINHEIRO -> "Dinheiro";
            case PIX -> "PIX";
            case CARTAO -> "Cartão";
            case FIADO -> "Fiado";
        };
    }

    // ============================== PRODUTOS ==============================

    private void configurarTabelaProdutos() {
        colPosicao.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(tabelaProdutos.getItems().indexOf(c.getValue()) + 1)));
        colProduto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduto().getNomeProduto()));
        colQtd.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getQuantidade() + (c.getValue().getProduto().isPesavel() ? " pes." : "")));
        colNumVendas.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getVendas())));
        colValor.setCellValueFactory(c -> new SimpleStringProperty(Money.format(c.getValue().getValor())));
        colParticipacao.setCellValueFactory(c -> new SimpleStringProperty(percentual(c.getValue().getValor(), totalProdutos)));
        colUltima.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUltimaVenda() == null ? "—" : c.getValue().getUltimaVenda().format(DATA_HORA)));
        // A posição é a do filtro atual; ordenar clicando no cabeçalho bagunçaria o "#"
        for (TableColumn<VendaProduto, ?> col : tabelaProdutos.getColumns()) col.setSortable(false);
    }

    private void mostrarProdutos() {
        totalProdutos = produtosDoPeriodo.stream().map(VendaProduto::getValor).reduce(Money.ZERO, BigDecimal::add);

        List<VendaProduto> venderam = produtosDoPeriodo.stream().filter(VendaProduto::vendeu).collect(Collectors.toList());
        long parados = produtosDoPeriodo.size() - venderam.size();
        lblProdVenderam.setText(String.valueOf(venderam.size()));
        lblProdVenderamDetalhe.setText("de " + produtosDoPeriodo.size() + " cadastrados");
        lblItens.setText(String.valueOf(venderam.stream().mapToInt(VendaProduto::getQuantidade).sum()));
        lblParados.setText(String.valueOf(parados));
        Tema.estado(lblParados, parados > 0 ? "texto-alerta" : null);

        Comparator<VendaProduto> ordem = comparador();
        Optional<VendaProduto> campeao = venderam.stream().min(ordem);
        lblCampeao.setText(campeao.map(v -> v.getProduto().getNomeProduto()).orElse("—"));
        lblCampeaoDetalhe.setText(campeao.map(v -> v.getQuantidade() + " itens · " + Money.format(v.getValor())).orElse(""));

        List<VendaProduto> lista = produtosDoPeriodo.stream()
                .filter(filtroMostrar())
                .filter(filtroTipo())
                .filter(v -> BuscaProduto.combina(v.getProduto(), txtBuscaProduto.getText(), BuscaProduto.Filtro.TODOS, false))
                .sorted(btnParados.isSelected()
                        ? Comparator.comparing((VendaProduto v) -> v.getProduto().getNomeProduto(), String.CASE_INSENSITIVE_ORDER)
                        : ordem)
                .collect(Collectors.toList());
        tabelaProdutos.setItems(FXCollections.observableArrayList(lista));
        mostrarTop(lista);
    }

    private Comparator<VendaProduto> comparador() {
        Comparator<VendaProduto> c = switch (comboOrdem.getValue() == null ? ORDEM_QTD : comboOrdem.getValue()) {
            case ORDEM_VALOR -> Comparator.comparing(VendaProduto::getValor);
            case ORDEM_VENDAS -> Comparator.comparingInt(VendaProduto::getVendas);
            default -> Comparator.comparingInt(VendaProduto::getQuantidade);
        };
        // maior primeiro; empate pelo nome
        return c.reversed().thenComparing(v -> v.getProduto().getNomeProduto(), String.CASE_INSENSITIVE_ORDER);
    }

    private Predicate<VendaProduto> filtroMostrar() {
        if (btnQueVenderam.isSelected()) return VendaProduto::vendeu;
        if (btnParados.isSelected()) return v -> !v.vendeu();
        return v -> true;
    }

    private Predicate<VendaProduto> filtroTipo() {
        String tipo = comboTipo.getValue() == null ? TIPO_TODOS : comboTipo.getValue();
        return switch (tipo) {
            case TIPO_PESAVEIS -> v -> v.getProduto().isPesavel();
            case TIPO_SEM_ESTOQUE -> v -> !v.getProduto().isControlaEstoque();
            case TIPO_SEM_FIADO -> v -> !v.getProduto().isPermiteFiado();
            default -> v -> true;
        };
    }

    /** Barras deitadas com os 10 primeiros da lista, o 1º em cima. */
    private void mostrarTop(List<VendaProduto> lista) {
        String ordem = comboOrdem.getValue() == null ? ORDEM_QTD : comboOrdem.getValue();
        List<VendaProduto> top = lista.stream().filter(VendaProduto::vendeu).limit(TOP).collect(Collectors.toList());
        graficoTop.setTitle("Top " + top.size() + " · " + ordem.toLowerCase());

        // Nomes repetidos (ex.: dois "cheetos") viram uma barra só no gráfico; o código desempata
        Set<String> usados = new HashSet<>();
        List<XYChart.Data<Number, String>> barras = new ArrayList<>();
        for (VendaProduto v : top) {
            String nome = v.getProduto().getNomeProduto();
            if (!usados.add(nome)) nome = nome + " (" + v.getProduto().getCodBarras() + ")";
            Number tamanho = switch (ordem) {
                case ORDEM_VALOR -> v.getValor().doubleValue();
                case ORDEM_VENDAS -> v.getVendas();
                default -> v.getQuantidade();
            };
            XYChart.Data<Number, String> d = new XYChart.Data<>(tamanho, nome);
            String dica = nome + "\n" + v.getQuantidade() + " itens em " + v.getVendas() + " vendas\n" + Money.format(v.getValor());
            d.nodeProperty().addListener((obs, antes, no) -> {
                if (no != null) Tooltip.install(no, new Tooltip(dica));
            });
            barras.add(d);
        }
        Collections.reverse(barras); // o eixo de categorias desenha o primeiro embaixo
        XYChart.Series<Number, String> serie = new XYChart.Series<>();
        serie.getData().setAll(barras);
        graficoTop.getData().setAll(List.of(serie));
    }

    // ============================== AJUDA ==============================

    private static BigDecimal dividir(BigDecimal valor, long por) {
        return valor.divide(BigDecimal.valueOf(por), Money.SCALE, RoundingMode.HALF_UP);
    }

    /** "12,5%"; "0%" quando o total é zero. */
    private static String percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return "0%";
        BigDecimal p = parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
        return p.toPlainString().replace('.', ',').replace(",0", "") + "%";
    }
}
