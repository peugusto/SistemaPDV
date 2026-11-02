package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.*;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.services.VendaSuspensaService;
import com.casarural.sistemapdv.util.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.stage.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Lista somente rascunhos disponíveis; não lança valores financeiros. */
public final class VendasSuspensasDialogo {
    public static final class Resultado {
        public final VendaSuspensa venda;
        public final List<OrderItem> itens;
        Resultado(VendaSuspensa venda,List<OrderItem> itens){this.venda=venda;this.itens=itens;}
    }
    private final VendaSuspensaService service;
    private final Stage janela=new Stage();
    private final TableView<VendaSuspensa> tabela=new TableView<>();
    private final Button retomar=new Button("Retomar venda"),descartar=new Button("Descartar"),atualizar=new Button("Atualizar"),fechar=new Button("Fechar");
    private final Label aviso=new Label("Selecione o carrinho. Os preços e o estoque serão conferidos antes da retomada.");
    private final ProtecaoPin protecao;
    private boolean ocupado;
    private Runnable pendente;
    private Resultado resultado;
    private VendasSuspensasDialogo(VendaSuspensaService service,Window dono,ProtecaoPin protecao){
        this.service=service;this.protecao=protecao;janela.setTitle("Vendas suspensas");janela.initModality(Modality.APPLICATION_MODAL);if(dono!=null)janela.initOwner(dono);
        coluna("Identificação",v->v.getIdentificacao());coluna("Horário",v->v.atualizadaEm.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")));
        coluna("Itens",v->String.valueOf(v.getItens()));coluna("Total",v->Money.format(v.getTotal()));coluna("Situação",VendaSuspensa::getSituacao);
        tabela.setId("tabelaSuspensas");retomar.setId("retomarSuspensa");descartar.setId("descartarSuspensa");atualizar.setId("atualizarSuspensas");fechar.setId("fecharSuspensas");
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);tabela.setPlaceholder(new Label("Nenhuma venda suspensa disponível."));
        tabela.getSelectionModel().selectedItemProperty().addListener((o,a,n)->botoes());
        tabela.setOnMouseClicked(e->{if(e.getClickCount()==2)retomar();});retomar.setOnAction(e->retomar());descartar.setOnAction(e->descartar());atualizar.setOnAction(e->carregar());fechar.setOnAction(e->{if(pendente==null)janela.close();});
        aviso.setWrapText(true);aviso.setMaxWidth(880);HBox acoes=new HBox(10,retomar,descartar,atualizar,fechar);
        VBox raiz=new VBox(12,new Label("Vendas suspensas"),aviso,tabela,acoes);raiz.setPadding(new Insets(20));VBox.setVgrow(tabela,Priority.ALWAYS);
        Scene cena=new Scene(raiz,920,480);Tema.aplicar(cena);janela.setScene(cena);
        janela.setOnCloseRequest(e->{if(pendente!=null||ocupado)e.consume();});botoes();
    }
    private void coluna(String nome,java.util.function.Function<VendaSuspensa,String> texto){TableColumn<VendaSuspensa,String> c=new TableColumn<>(nome);c.setCellValueFactory(v->new SimpleStringProperty(texto.apply(v.getValue())));tabela.getColumns().add(c);}
    private void botoes(){boolean selecionou=tabela.getSelectionModel().getSelectedItem()!=null;retomar.setDisable(ocupado||pendente==null&&!selecionou);descartar.setDisable(ocupado||pendente!=null||!selecionou);atualizar.setDisable(ocupado||pendente!=null);fechar.setDisable(ocupado||pendente!=null);}
    private void carregar(){
        if(ocupado||pendente!=null)return;
        try{ocupado=true;botoes();tabela.setItems(FXCollections.observableArrayList(service.listar()));}
        catch(Exception e){Alerts.showAlert("Vendas suspensas",null,e.getMessage(),Alert.AlertType.WARNING);}
        finally{ocupado=false;if(pendente==null)retomar.setText("Retomar venda");botoes();}
    }
    private boolean conferirPagamento(VendaSuspensa v){
        if(v.estado!=VendaSuspensa.Estado.EM_PAGAMENTO)return true;
        if(!protecao.autorizar(janela,"revisar carrinho com pagamento interrompido"))return false;
        return Alerts.showConfirmation("Pagamento interrompido","Confira antes de continuar",
                "Consulte o histórico e confira os valores já recebidos no dinheiro, PIX ou cartão.\n\n"
                        +"Esta retomada não recupera nem compensa pagamentos anteriores. Resolva os valores recebidos e a conferência do caixa antes de continuar, para não cobrar novamente.\n\nJá conferiu e pode continuar?");
    }
    private void executar(Runnable acao){
        if(ocupado)return;ocupado=true;botoes();
        try{acao.run();pendente=null;retomar.setText("Retomar venda");}
        catch(ResultadoTransacaoIncertoException e){pendente=acao;retomar.setText("Confirmar tentativa");aviso.setText("A resposta da gravação não chegou. Confirme a mesma tentativa, sem fechar esta lista. Nenhuma venda foi lançada por esta lista.");}
        catch(VendaSuspensaIndisponivelException e){pendente=null;Alerts.showAlert("Carrinho indisponível",null,e.getMessage(),Alert.AlertType.WARNING);}
        catch(Exception e){Alerts.showAlert("Vendas suspensas",null,e.getMessage(),Alert.AlertType.WARNING);}
        finally{ocupado=false;if(pendente==null)retomar.setText("Retomar venda");botoes();}
    }
    private void retomar(){
        if(ocupado)return;if(pendente!=null){executar(pendente);return;}
        VendaSuspensa v=tabela.getSelectionModel().getSelectedItem();if(v==null)return;
        try{
            RetomadaVenda p=service.preparar(v.id);
            if(p.itens.isEmpty()){Alerts.showAlert("Carrinho sem itens disponíveis",null,String.join("\n",p.avisos)+"\nAtualize os produtos ou descarte este carrinho.",Alert.AlertType.WARNING);return;}
            boolean conferido=conferirPagamento(p.original);if(!conferido)return;
            if(!p.avisos.isEmpty()&&!conferirAlteracoes(p))return;
            UUID op=UUID.randomUUID(),token=UUID.randomUUID();
            executar(()->{VendaSuspensa retomada=service.retomar(p,op,token,conferido);resultado=new Resultado(retomada,p.itens);janela.hide();});
        }catch(Exception e){Alerts.showAlert("Vendas suspensas",null,e.getMessage(),Alert.AlertType.WARNING);}
    }
    private boolean conferirAlteracoes(RetomadaVenda p){
        Alert a=new Alert(Alert.AlertType.CONFIRMATION);a.initOwner(janela);a.setTitle("Conferir retomada");
        a.setHeaderText("Total anterior: "+Money.format(p.original.getTotal())+" • Novo total: "+Money.format(VendaSuspensa.total(VendaSuspensa.capturar(p.itens))));
        TextArea texto=new TextArea(String.join("\n",p.avisos));texto.setEditable(false);texto.setWrapText(true);texto.setPrefRowCount(12);texto.setPrefColumnCount(65);
        a.getDialogPane().setContent(texto);a.getDialogPane().getButtonTypes().setAll(new ButtonType("Retomar com alterações",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);
        Tema.aplicar(a.getDialogPane());return a.showAndWait().map(t->t.getButtonData()==ButtonBar.ButtonData.OK_DONE).orElse(false);
    }
    private void descartar(){
        if(ocupado||pendente!=null)return;VendaSuspensa v=tabela.getSelectionModel().getSelectedItem();if(v==null)return;
        boolean conferido=conferirPagamento(v);if(!conferido)return;
        if(!Alerts.showConfirmation("Descartar carrinho",null,"Descartar "+v.getIdentificacao()+"? Isso não cancela vendas nem altera estoque, caixa ou fiado."))return;
        UUID op=UUID.randomUUID();executar(()->{service.descartar(v,op,conferido);tabela.getItems().remove(v);});
    }
    public static Optional<Resultado> abrir(VendaSuspensaService service,Window dono){
        VendasSuspensasDialogo d=new VendasSuspensasDialogo(service,dono,new ProtecaoPin());d.carregar();d.janela.showAndWait();return Optional.ofNullable(d.resultado);
    }
}
