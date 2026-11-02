package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.model.entities.VendaSuspensa;
import com.casarural.sistemapdv.model.entities.VendaSuspensa.Estado;
import com.casarural.sistemapdv.model.entities.VendaSuspensa.Gravacao;
import com.casarural.sistemapdv.services.VendaSuspensaService;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Money;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class PDVController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(PDVController.class);

    private ProductService productService;
    private ObservableList<OrderItem> itensVenda = FXCollections.observableArrayList();
    /** Troca em andamento: a venda que vai ser substituída (null = venda normal). */
    private VendaParaDesfazer trocaDe;
    /** Na troca, o estoque que a venda antiga devolve ao ser cancelada (id do produto -> quantidade). */
    private final Map<Integer, Integer> estoqueDaTroca = new HashMap<>();
    private Runnable aoConcluirTroca;
    private boolean trocaDesatualizada;
    public boolean isTrocaDesatualizada() { return trocaDesatualizada; }
    private VendaSuspensaService suspensas;
    private int quantidadeSuspensas=-1;
    private VendaSuspensa rascunho;
    private Gravacao pendente;
    private boolean operando,carregando,rascunhoBloqueado,finalizando;
    private PaymentController pagamentoRetido;
    private java.util.List<OrderItem> ultimoCarrinhoConfirmado=java.util.List.of();
    @FXML private Button botaoSuspender,botaoRetomar;
    @FXML private Label labelVendaSuspensa;
    public void setVendaSuspensaService(VendaSuspensaService service){this.suspensas=service;atualizarContagem();atualizarAcoes();}


    @FXML private Label labelUltimoItem;
    @FXML private TextField campoCodigoProduto;
    @FXML private TextField campoQuantidade;
    @FXML private TextField campoValorUnitario;
    @FXML private TextField campoTotalItem;


    @FXML private ListView<OrderItem> listaItens;
    @FXML private Label labelTotal;
    @FXML private Label labelDescontoTroca;


    @FXML private Button botaoAdicionar;
    @FXML private Button botaoBuscar;
    @FXML private Button botaoRemover;
    @FXML private Button botaoLimpar;
    @FXML private Button botaoFinalizar;

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {
        listaItens.setItems(itensVenda);
        listaItens.setCellFactory(param -> new ListCell<OrderItem>() {
            @Override
            protected void updateItem(OrderItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox hBox = new HBox(10);

                    Label lQtd = new Label(String.valueOf(item.getQtd()));
                    lQtd.setPrefWidth(40);

                    // Produto que não pode ir para o fiado aparece marcado no carrinho
                    Label lDesc = new Label(item.getProduto().getNomeProduto()
                            + (item.getProduto().isPesavel() ? "  (balança)" : "")
                            + (item.getProduto().isPermiteFiado() ? "" : "  [sem fiado]"));
                    lDesc.setPrefWidth(200);
                    lDesc.setStyle("-fx-wrap-text: true;");

                    Label lVal = new Label(Money.formatPlain(item.getPrecoUnitario()));
                    lVal.setPrefWidth(70);

                    Label lTot = new Label(Money.formatPlain(item.getSubtotal()));
                    lTot.setPrefWidth(70);

                    hBox.getChildren().addAll(lQtd, lDesc, lVal, lTot);
                    setGraphic(hBox);
                }
            }
        });


        botaoAdicionar.setOnAction(event -> onAdicionarItem());
        campoCodigoProduto.setOnAction(event -> onAdicionarItem()); // Permite adicionar apertando Enter no campo

        botaoBuscar.setOnAction(event -> onBuscarProduto());
        botaoRemover.setOnAction(event -> onRemoverItem());
        botaoLimpar.setOnAction(event -> onLimparVenda());
        botaoFinalizar.setOnAction(event -> onFinalizarVenda());


        labelTotal.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == javafx.scene.input.KeyCode.F1) {
                        onFinalizarVenda();
                        event.consume();
                    }
                    if (event.getCode() == javafx.scene.input.KeyCode.F2) {
                        onBuscarProduto();
                        event.consume();
                    }
                    if(event.getCode()==javafx.scene.input.KeyCode.F3){onSuspenderVenda();event.consume();}
                    if(event.getCode()==javafx.scene.input.KeyCode.F4){onRetomarVenda();event.consume();}
                    // Mesmo vazio, o carrinho pode ter uma gravação pendente ou ainda possuir um rascunho.
                    if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && (trocaDe != null || !itensVenda.isEmpty()
                            || rascunho!=null||pendente!=null||rascunhoBloqueado||pagamentoRetido!=null)) {
                        onLimparVenda();
                        event.consume();
                    }
                });
            }
        });
        botaoSuspender.setOnAction(e->onSuspenderVenda());botaoRetomar.setOnAction(e->onRetomarVenda());
        labelTotal.sceneProperty().addListener((o,a,cena)->{
            if(cena!=null)cena.windowProperty().addListener((w,antiga,janela)->{
                if(janela!=null)janela.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST,e->{if(!prepararSaida())e.consume();});
            });
        });
    }

    private void onAdicionarItem() {
        if(!podeEditar())return;
        String codigo = campoCodigoProduto.getText() == null ? "" : campoCodigoProduto.getText().trim();
        if (codigo.isEmpty()) return;


        Integer qtdInput = quantidadeDoFormulario();
        if (qtdInput == null) return;

        Optional<Product> prodOpt = productService.findByCodBarras(codigo);

        if (prodOpt.isPresent() && !prodOpt.get().isAtivo()) {
            Alerts.showAlert("Produto desativado", prodOpt.get().getNomeProduto(),
                    "Este produto foi desativado e não pode ser vendido. Para vender de novo, marque "
                            + "\"Produto ativo\" em Estoque > Editar.", Alert.AlertType.WARNING);
            campoCodigoProduto.selectAll();
            return;
        }
        if (prodOpt.isPresent()) {
            Product p = prodOpt.get();

            // Pesável: vale o valor da balança, cada pesagem é uma linha própria
            if (p.isPesavel()) {
                adicionarPesavel(p);
                return;
            }

            final int quantidadeFinal = qtdInput;

            Optional<OrderItem> itemExistente = itensVenda.stream()
                    .filter(item -> item.getProduto().getCodBarras().equals(codigo))
                    .findFirst();

            int qtdNoCarrinho = itemExistente.map(OrderItem::getQtd).orElse(0);
            int quantidadeTotal;
            try {
                quantidadeTotal = Math.addExact(qtdNoCarrinho, quantidadeFinal);
            } catch (ArithmeticException e) {
                avisarQuantidadeInvalida("A quantidade total deste produto no cupom não pode passar de 2.147.483.647.");
                return;
            }
            int devolveNaTroca = estoqueDaTroca.getOrDefault(p.getIdProduto(), 0);
            if (!p.temEstoquePara(Math.max(0, quantidadeTotal - devolveNaTroca))) {
                Alerts.showAlert("Estoque Insuficiente", null,
                        String.format("Estoque disponível de '%s': %d (já no carrinho: %d).",
                                p.getNomeProduto(), Math.max(0, p.getEstoque()), qtdNoCarrinho),
                        Alert.AlertType.WARNING);
                campoCodigoProduto.selectAll();
                return;
            }

            campoValorUnitario.setText(Money.formatPlain(p.getPrecoProduto()));
            campoTotalItem.setText(Money.formatPlain(p.getPrecoProduto().multiply(BigDecimal.valueOf(quantidadeFinal))));
            labelUltimoItem.setText(quantidadeFinal + " X " + p.getNomeProduto().toUpperCase());

            if (itemExistente.isPresent()) {
                OrderItem item = itemExistente.get();
                item.setQtd(quantidadeTotal);
                listaItens.refresh(); // Atualiza o visual da lista
            } else {
                OrderItem novoItem = new OrderItem(p, quantidadeFinal, p.getPrecoProduto());
                itensVenda.add(novoItem);
            }

            atualizarTotalVenda();


            campoCodigoProduto.clear();
            campoQuantidade.setText("1");
            campoCodigoProduto.requestFocus();


            listaItens.scrollTo(itensVenda.size() - 1);
        } else {
            Alerts.showAlert("Aviso", null, "Produto não encontrado!", Alert.AlertType.WARNING);
            campoCodigoProduto.selectAll();
        }
    }

    /** Vazio conserva o padrão do leitor; texto inválido não pode incluir uma unidade por engano. */
    private Integer quantidadeDoFormulario() {
        String texto = campoQuantidade.getText();
        if (texto == null || texto.isBlank()) return 1;
        try {
            int quantidade = Integer.parseInt(texto.trim());
            if (quantidade > 0) return quantidade;
        } catch (NumberFormatException e) {
            // A mensagem abaixo também cobre decimais, texto e valores fora de int.
        }
        avisarQuantidadeInvalida("Digite uma quantidade inteira entre 1 e 2.147.483.647. "
                + "Deixe o campo vazio para adicionar uma unidade.");
        return null;
    }

    private void avisarQuantidadeInvalida(String mensagem) {
        Alerts.showAlert("Quantidade inválida", "O produto não foi adicionado", mensagem, Alert.AlertType.WARNING);
        campoQuantidade.requestFocus();
        campoQuantidade.selectAll();
    }

    /**
     * Queijo, mortadela...: a balança mostra o preço e o caixa digita esse valor.
     * Entra como 1 item com o valor digitado; não mexe no estoque.
     */
    private void adicionarPesavel(Product p) {
        Optional<BigDecimal> valor = Alerts.pedirValor(
                "Produto pesado",
                p.getNomeProduto().toUpperCase() + "  —  " + Money.format(p.getPrecoProduto()) + " o kg",
                "Valor na balança (R$):");
        if (valor.isEmpty()) {
            campoCodigoProduto.selectAll();
            campoCodigoProduto.requestFocus();
            return;
        }

        itensVenda.add(new OrderItem(p, 1, valor.get()));
        campoValorUnitario.setText(Money.formatPlain(valor.get()));
        campoTotalItem.setText(Money.formatPlain(valor.get()));
        labelUltimoItem.setText(p.getNomeProduto().toUpperCase() + " (BALANÇA)");
        atualizarTotalVenda();

        campoCodigoProduto.clear();
        campoQuantidade.setText("1");
        campoCodigoProduto.requestFocus();
        listaItens.scrollTo(itensVenda.size() - 1);
    }

    /**
     * F2: procura pelo nome. O produto escolhido entra como se o código tivesse sido digitado,
     * então vale a quantidade do campo, a janela da balança e a conferência de estoque.
     */
    private void onBuscarProduto() {
        if(!podeEditar())return;
        if (quantidadeDoFormulario() == null) return;
        try {
            Optional<Product> escolhido = BuscaProdutoController.abrir(productService, labelTotal.getScene().getWindow());
            if (escolhido.isPresent()) {
                campoCodigoProduto.setText(escolhido.get().getCodBarras());
                onAdicionarItem();
            } else {
                campoCodigoProduto.requestFocus();
            }
        } catch (Exception e) {
            log.error("Erro ao abrir a busca de produto", e);
            Alerts.showAlert("Buscar produto", null, "Não foi possível abrir a busca de produto.", Alert.AlertType.ERROR);
        }
    }

    private void onRemoverItem() {
        if(!podeEditar())return;
        OrderItem selecionado = listaItens.getSelectionModel().getSelectedItem();
        if (selecionado != null) {
            itensVenda.remove(selecionado);
            atualizarTotalVenda();
            labelUltimoItem.setText("ITEM REMOVIDO");
            campoValorUnitario.setText("0,00");
            campoTotalItem.setText("0,00");
            campoCodigoProduto.requestFocus();
        } else {
            Alerts.showAlert("Seleção", null, "Selecione um item na lista para remover.", Alert.AlertType.INFORMATION);
        }
    }

    private void onLimparVenda() {
        if(operando||finalizando||pendente!=null||rascunhoBloqueado)return;
        if(pagamentoRetido!=null||rascunho!=null&&rascunho.estado==Estado.EM_PAGAMENTO){Alerts.showAlert("Pagamento em andamento",null,"Use Continuar pagamento para conferir a finalização antes de cancelar o carrinho.",Alert.AlertType.WARNING);return;}
        if (trocaDe != null) {
            if (Alerts.showConfirmation("Desistir da troca", null,
                    "Desistir da troca? A venda #" + trocaDe.getIdPedido() + " continua como estava.")) {
                fecharJanela();
            }
            return;
        }
        if (itensVenda.isEmpty()&&rascunho==null) return;

        if (Alerts.showConfirmation("Cancelar Venda", null, "Deseja cancelar toda a venda atual?")) {
            if(rascunho!=null){gravarSuspensa(Estado.DESCARTADA,rascunho.identificacao);return;}
            itensVenda.clear();
            atualizarTotalVenda();
            resetarCamposVisuais();
        }
    }

    /**
     * Começa uma troca: o cupom vem com os itens da venda antiga. O caixa tira o produto errado,
     * passa o certo e finaliza; o que o cliente já pagou entra como crédito no pagamento.
     */
    public void iniciarTroca(VendaParaDesfazer venda, Runnable aoConcluir) {
        Map<Integer, Integer> devolucoes = new HashMap<>();
        for (OrderItem item : venda.getItens()) {
            if (item.getEstoqueBaixado() == null) throw new IllegalArgumentException("Venda sem registro da baixa de estoque: é necessário conferir antes de trocar.");
            try { devolucoes.merge(item.getProduto().getIdProduto(), item.getEstoqueBaixado(), Math::addExact); }
            catch (ArithmeticException e) { throw new IllegalArgumentException("A quantidade devolvida ultrapassa o limite de estoque.", e); }
        }
        this.trocaDe = venda;
        this.aoConcluirTroca = aoConcluir;
        estoqueDaTroca.clear();
        estoqueDaTroca.putAll(devolucoes);
        itensVenda.clear();
        for (OrderItem i : venda.getItens()) {
            itensVenda.add(new OrderItem(i.getProduto(), i.getQtd(), i.getPrecoUnitario()));

        }
        atualizarTotalVenda();
        labelUltimoItem.setText("TROCA DA VENDA #" + venda.getIdPedido());
        atualizarAcoes();
        campoCodigoProduto.requestFocus();
    }

    private void fecharJanela() {
        ((javafx.stage.Stage) labelTotal.getScene().getWindow()).close();
    }

    private void onFinalizarVenda() {
        if(operando||finalizando||pendente!=null||rascunhoBloqueado)return;
        if (itensVenda.isEmpty()) {
            Alerts.showAlert("Aviso", "Carrinho Vazio", "Adicione produtos antes de finalizar a venda.", Alert.AlertType.WARNING);
            return;
        }

        finalizando=true;atualizarAcoes();
        try {
        PaymentController[] pagamento = new PaymentController[1];
        if(pagamentoRetido!=null){
            pagamento[0]=pagamentoRetido;continuarPagamento(pagamentoRetido);
        }else{
        if(rascunho!=null&&rascunho.estado!=Estado.EM_PAGAMENTO&&!gravarSuspensa(Estado.EM_PAGAMENTO,rascunho.identificacao))return;
        pagamento[0]=abrirPagamento(prepararPedido());
        }

        if (pagamento[0] != null && pagamento[0].isTrocaDesatualizada()) {
            trocaDesatualizada=true;
            fecharJanela();
            return;
        }
        // Resultado incerto não é cancelamento: reabrir este carrinho criaria outra venda.
        if (pagamento[0] != null && pagamento[0].isVendaPendenteConferencia()) {
            liberarPagamentoInterrompido();
            fecharJanela();
            return;
        }

        // Se o pagamento foi cancelado, mantém o carrinho para o operador continuar a venda
        if (pagamento[0] != null && pagamento[0].isVendaConcluida() && trocaDe != null) {
            // Troca feita: volta para o histórico
            if (aoConcluirTroca != null) aoConcluirTroca.run();
            fecharJanela();
        } else if (pagamento[0] != null && pagamento[0].isVendaConcluida()) {
            rascunho=null;pagamentoRetido=null;
            itensVenda.clear();
            atualizarTotalVenda();
            resetarCamposVisuais();
            atualizarContagem();
        } else {
            if(pagamento[0]!=null&&pagamento[0].isHouveLancamento())pagamentoRetido=pagamento[0];
            else if(rascunho!=null)gravarSuspensa(Estado.EM_USO,rascunho.identificacao);
            campoCodigoProduto.requestFocus();
        }
        atualizarAcoes();
        } finally {finalizando=false;atualizarAcoes();if(podeEditar())campoCodigoProduto.requestFocus();}
    }

    protected PaymentController abrirPagamento(Order order){
        PaymentController[] pagamento=new PaymentController[1];
        com.casarural.sistemapdv.util.ViewLoader.showView(true,"/com/casarural/sistemapdv/view/FechamentoVenda.fxml","Finalizar Pagamento - Casa Rural",(PaymentController controller)->{
            pagamento[0]=controller;controller.setDadosVenda(order,new com.casarural.sistemapdv.services.OrderService(),
                    new com.casarural.sistemapdv.services.CustomerService(com.casarural.sistemapdv.model.dao.DaoFactory.createCustomerDAO()));
            if(trocaDe!=null)controller.setTroca(trocaDe);
        });return pagamento[0];
    }
    protected void continuarPagamento(PaymentController pagamento){pagamento.getJanela().showAndWait();}

    Order prepararPedido() {
        Order pedido = new Order();
        pedido.setItemPedido(new java.util.ArrayList<>(itensVenda));
        pedido.aplicarDesconto(com.casarural.sistemapdv.model.entities.RegraTroca.descontoOriginal(trocaDe, itensVenda));
        if (trocaDe != null) {
            pedido.setIdPedidoTrocado(trocaDe.getIdPedido());
            pedido.setVersaoPedidoTrocado(trocaDe.getVersao());
        }
        pedido.setStatus(com.casarural.sistemapdv.model.entities.enums.OrderStatus.PAGO);
        pedido.setVendaSuspensa(rascunho);
        return pedido;
    }

    private BigDecimal calcularTotalVenda() {
        return itensVenda.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.ZERO, BigDecimal::add)
                .subtract(com.casarural.sistemapdv.model.entities.RegraTroca.descontoOriginal(trocaDe,itensVenda));
    }

    private void atualizarTotalVenda() {
        if(!carregando&&rascunho!=null&&rascunho.estado==Estado.EM_USO&&pendente==null&&!rascunhoBloqueado) {
            try{if(!VendaSuspensa.capturar(itensVenda).equals(rascunho.itens))gravarSuspensa(Estado.EM_USO,rascunho.identificacao);}
            catch(IllegalArgumentException e){itensVenda.setAll(copiarItens(ultimoCarrinhoConfirmado));Alerts.showAlert("Carrinho não alterado",null,e.getMessage(),Alert.AlertType.WARNING);}
        }
        labelTotal.setText(Money.format(calcularTotalVenda()));
        boolean mostrar = trocaDe != null && Money.isPositive(trocaDe.getDesconto());
        labelDescontoTroca.setVisible(mostrar);
        labelDescontoTroca.setManaged(mostrar);
        if (mostrar) {
            BigDecimal mantido = com.casarural.sistemapdv.model.entities.RegraTroca.descontoOriginal(trocaDe, itensVenda);
            labelDescontoTroca.setText(Money.isPositive(mantido) ? "Desconto original mantido: " + Money.format(mantido)
                    : "Carrinho alterado: desconto original removido. Novo desconto exige PIN no pagamento.");
        }
        atualizarAcoes();
    }

    private boolean podeEditar(){return !operando&&!finalizando&&pendente==null&&!rascunhoBloqueado&&pagamentoRetido==null&&(rascunho==null||rascunho.estado==Estado.EM_USO);}
    private void atualizarAcoes(){
        boolean edita=podeEditar();
        botaoAdicionar.setDisable(!edita);botaoBuscar.setDisable(!edita);botaoRemover.setDisable(!edita);botaoLimpar.setDisable(!edita);
        campoCodigoProduto.setDisable(!edita);campoQuantidade.setDisable(!edita);
        botaoFinalizar.setDisable(operando||finalizando||pendente!=null||rascunhoBloqueado||itensVenda.isEmpty());
        botaoFinalizar.setText(pagamentoRetido==null&&(rascunho==null||rascunho.estado!=Estado.EM_PAGAMENTO)?"Finalizar venda (F1)":"Continuar pagamento (F1)");
        botaoSuspender.setDisable(suspensas==null||trocaDe!=null||operando||finalizando||rascunhoBloqueado||pagamentoRetido!=null||pendente==null&&(itensVenda.isEmpty()||rascunho!=null&&rascunho.estado!=Estado.EM_USO));
        botaoSuspender.setText(pendente==null?"Suspender venda (F3)":"Confirmar salvamento (F3)");
        botaoRetomar.setDisable(suspensas==null||quantidadeSuspensas==0||trocaDe!=null||!edita||rascunho!=null||!itensVenda.isEmpty());
        String status=rascunho==null?"":"Carrinho retomado: "+rascunho.getIdentificacao();
        if(pendente!=null)status="Salvamento pendente. Confirme a mesma tentativa antes de continuar.";
        if(rascunhoBloqueado)status="Carrinho indisponível. Feche esta tela e confira a lista.";
        if(pagamentoRetido!=null)status="Há pagamentos lançados. Use Continuar pagamento; o carrinho não pode ser suspenso.";
        labelVendaSuspensa.setText(status);labelVendaSuspensa.setVisible(!status.isEmpty());labelVendaSuspensa.setManaged(!status.isEmpty());
    }
    private void atualizarContagem(){
        quantidadeSuspensas=-1;
        if(suspensas==null)return;
        try{quantidadeSuspensas=suspensas.listar().size();botaoRetomar.setText("Retomar venda ("+quantidadeSuspensas+") (F4)");}
        catch(Exception e){botaoRetomar.setText("Retomar venda (F4)");log.warn("Não foi possível atualizar a contagem de carrinhos",e);}
        finally{atualizarAcoes();}
    }
    private boolean gravarSuspensa(Estado destino,String nome){
        if(suspensas==null)return false;
        try{
            if(pendente==null)pendente=suspensas.gravacao(rascunho,nome,itensVenda,destino);
            return confirmarGravacao();
        }catch(Exception e){Alerts.showAlert("Salvar carrinho",null,e.getMessage(),Alert.AlertType.WARNING);return false;}
    }
    private boolean confirmarGravacao(){
        if(operando||pendente==null)return false;operando=true;atualizarAcoes();
        try{
            VendaSuspensa salva=suspensas.gravar(pendente);pendente=null;
            if(salva.estado==Estado.CONCLUIDA){rascunhoBloqueado=true;Alerts.showAlert("Venda já finalizada",null,"Confira a venda #"+salva.pedido+" no histórico. Não registre o carrinho novamente.",Alert.AlertType.WARNING);return false;}
            if(salva.estado==Estado.SUSPENSA||salva.estado==Estado.DESCARTADA){rascunho=null;carregando=true;itensVenda.clear();atualizarTotalVenda();carregando=false;resetarCamposVisuais();atualizarContagem();}
            else {rascunho=salva;ultimoCarrinhoConfirmado=copiarItens(itensVenda);}
            return true;
        }catch(com.casarural.sistemapdv.db.VendaSuspensaIndisponivelException e){pendente=null;rascunhoBloqueado=true;Alerts.showAlert("Carrinho indisponível",null,e.getMessage(),Alert.AlertType.WARNING);return false;}
        catch(Exception e){Alerts.showAlert("Salvamento não confirmado",null,"O carrinho continua nesta tela. Use Confirmar salvamento para repetir a mesma tentativa, sem alterar os itens.\n\n"+e.getMessage(),Alert.AlertType.WARNING);return false;}
        finally{operando=false;atualizarAcoes();if(podeEditar())campoCodigoProduto.requestFocus();}
    }
    @FXML public void onSuspenderVenda(){
        if(suspensas==null||trocaDe!=null||operando||finalizando||rascunhoBloqueado)return;
        if(pendente!=null){confirmarGravacao();return;}
        if(rascunho!=null&&rascunho.estado!=Estado.EM_USO)return;
        if(pagamentoRetido!=null){Alerts.showAlert("Pagamento em andamento",null,"Confira e conclua os pagamentos antes de suspender outra venda.",Alert.AlertType.WARNING);return;}
        if(itensVenda.isEmpty())return;
        TextInputDialog d=new TextInputDialog(rascunho==null?"":rascunho.identificacao);d.setTitle("Suspender venda");d.setHeaderText("Identificação opcional do carrinho");d.setContentText("Nome ou observação (até 80 caracteres):");
        if(labelTotal.getScene()!=null&&labelTotal.getScene().getWindow()!=null)d.initOwner(labelTotal.getScene().getWindow());
        com.casarural.sistemapdv.util.Tema.aplicar(d.getDialogPane());d.showAndWait().ifPresent(nome->gravarSuspensa(Estado.SUSPENSA,nome));
    }
    @FXML public void onRetomarVenda(){
        if(suspensas==null||quantidadeSuspensas==0||trocaDe!=null||operando||!podeEditar())return;
        if(!itensVenda.isEmpty()||rascunho!=null){Alerts.showAlert("Carrinho ocupado",null,"Suspenda ou cancele o carrinho atual antes de retomar outro.",Alert.AlertType.WARNING);return;}
        try{
            VendasSuspensasDialogo.abrir(suspensas,labelTotal.getScene().getWindow()).ifPresent(this::carregarRetomada);
        }finally{atualizarContagem();atualizarAcoes();}
    }
    void carregarRetomada(VendasSuspensasDialogo.Resultado resultado){
        rascunho=resultado.venda;carregando=true;itensVenda.clear();
        for(OrderItem i:resultado.itens)itensVenda.add(new OrderItem(i.getProduto(),i.getQtd(),i.getPrecoUnitario()));
        ultimoCarrinhoConfirmado=copiarItens(itensVenda);atualizarTotalVenda();carregando=false;labelUltimoItem.setText("VENDA RETOMADA");campoCodigoProduto.requestFocus();
    }
    private static java.util.List<OrderItem> copiarItens(java.util.List<OrderItem> itens){java.util.List<OrderItem> r=new java.util.ArrayList<>();for(OrderItem i:itens)r.add(new OrderItem(i.getProduto(),i.getQtd(),i.getPrecoUnitario()));return r;}
    private void liberarPagamentoInterrompido(){
        if(rascunho==null)return;
        try{suspensas.gravar(suspensas.interromperPagamento(rascunho));}
        catch(Exception e){log.warn("Carrinho com finalização interrompida deve ser conferido na próxima abertura",e);}
    }
    private boolean prepararSaida(){
        if(finalizando)return false;
        if(trocaDe!=null)return Alerts.showConfirmation("Desistir da troca",null,"Desistir da troca? A venda original continua como estava.");
        if(operando||pendente!=null){Alerts.showAlert("Salvamento pendente",null,"Confirme o salvamento do carrinho antes de fechar.",Alert.AlertType.WARNING);return false;}
        if(rascunhoBloqueado)return Alerts.showConfirmation("Fechar carrinho indisponível",null,"Fechar esta cópia? Confira a versão salva pela lista antes de continuar.");
        if(pagamentoRetido!=null||rascunho!=null&&rascunho.estado==Estado.EM_PAGAMENTO){
            if(rascunho==null){Alerts.showAlert("Pagamento em andamento",null,"Continue e confira o pagamento antes de fechar esta venda.",Alert.AlertType.WARNING);return false;}
            if(!Alerts.showConfirmation("Pagamento interrompido",null,"Deixar este carrinho pendente para conferência? Os pagamentos lançados nesta tela não serão compensados automaticamente. Confira o histórico e os valores recebidos antes de retomar."))return false;
            liberarPagamentoInterrompido();return true;
        }
        if(rascunho!=null){return gravarSuspensa(itensVenda.isEmpty()?Estado.DESCARTADA:Estado.SUSPENSA,rascunho.identificacao);}
        if(itensVenda.isEmpty())return true;
        int escolha=Alerts.showChoice("Sair da venda",null,"Há produtos no carrinho. Deseja guardar para continuar depois?",Alert.AlertType.CONFIRMATION,"Continuar na venda","Suspender e sair","Descartar e sair");
        if(escolha==0){onSuspenderVenda();return itensVenda.isEmpty();}return escolha==1;
    }

    private void resetarCamposVisuais() {
        labelUltimoItem.setText("CAIXA LIVRE");
        campoCodigoProduto.clear();
        campoQuantidade.setText("1");
        campoValorUnitario.setText("0,00");
        campoTotalItem.setText("0,00");
        campoCodigoProduto.requestFocus();
    }
}
