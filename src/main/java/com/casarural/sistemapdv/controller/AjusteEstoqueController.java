package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.services.AjusteEstoqueService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import java.util.UUID;

public class AjusteEstoqueController {
    @FXML private Label titulo,produto,saldoAtual,rotuloQuantidade,saldoNovo,status;
    @FXML private TextField quantidade;
    @FXML private TextArea motivo;
    @FXML private Button btnSalvar,btnCancelar;
    private ProtecaoPin protecao=new ProtecaoPin();
    private AjusteEstoqueService service;
    private int id,saldo;
    private String nome;
    private AjusteEstoque.Tipo tipo;
    private AjusteEstoque pendente;
    private boolean operando,concluido;
    @FXML public void initialize(){
        quantidade.textProperty().addListener((o,a,n)->prever());
        titulo.sceneProperty().addListener((o,a,cena)->{if(cena!=null){
            cena.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.ESCAPE){onCancelar();e.consume();}});
            cena.windowProperty().addListener((w,anterior,janela)->{if(janela!=null)janela.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST,e->{if(operando||pendente!=null){e.consume();avisarPendente();}});});
        }});
    }
    public void configurar(Product p,AjusteEstoque.Tipo tipo,AjusteEstoqueService service){
        if(p==null||p.getIdProduto()<=0||!p.isControlaEstoque())throw new IllegalArgumentException("Selecione um produto que controla estoque por unidade.");
        this.id=p.getIdProduto();this.saldo=p.getEstoque();this.nome=p.getNomeProduto();this.tipo=tipo;this.service=service;
        titulo.setText(tipo==AjusteEstoque.Tipo.PERDA?"Registrar perda":"Corrigir contagem");produto.setText(nome+" · "+p.getCodBarras());saldoAtual.setText("Estoque conferido no sistema: "+saldo);
        rotuloQuantidade.setText(tipo==AjusteEstoque.Tipo.PERDA?"Quantas unidades foram perdidas?":"Quantas unidades você contou fisicamente?");
        quantidade.setText(tipo==AjusteEstoque.Tipo.PERDA?"1":String.valueOf(saldo));prever();atualizar();
    }
    private int valor(){
        try{if(quantidade.getText()==null||quantidade.getText().isBlank())throw new NumberFormatException();return Integer.parseInt(quantidade.getText().trim());}
        catch(NumberFormatException e){throw new IllegalArgumentException("Digite uma quantidade inteira entre 0 e 2.147.483.647.");}
    }
    private void prever(){
        if(tipo==null)return;
        try{int v=valor();if(v<0||tipo==AjusteEstoque.Tipo.PERDA&&(v==0||v>saldo))throw new IllegalArgumentException();saldoNovo.setText("Estoque após confirmar: "+(tipo==AjusteEstoque.Tipo.PERDA?saldo-v:v));}
        catch(IllegalArgumentException e){saldoNovo.setText("Confira a quantidade informada.");}
    }
    private void atualizar(){
        boolean bloqueado=operando||pendente!=null||concluido;quantidade.setDisable(bloqueado);motivo.setDisable(bloqueado);
        btnCancelar.setDisable(operando||pendente!=null);btnSalvar.setDisable(operando||concluido);
        btnSalvar.setText(pendente==null?"Confirmar ajuste":"Confirmar tentativa");
        status.setText(pendente==null?"":"Salvamento pendente. Confirme a mesma tentativa antes de continuar.");
    }
    @FXML public void onSalvar(){
        if(operando||concluido||service==null)return;
        if(pendente==null)try{
            AjusteEstoque novo=new AjusteEstoque(UUID.randomUUID(),id,tipo,saldo,valor(),motivo.getText());
            if(!protecao.autorizar(titulo.getScene().getWindow(),(tipo==AjusteEstoque.Tipo.PERDA?"registrar perda":"corrigir contagem")+" de "+nome+" ("+saldo+" → "+novo.saldoDepois()+")"))return;
            pendente=novo;
        }catch(IllegalArgumentException e){Alerts.showAlert("Ajuste não registrado",null,e.getMessage(),Alert.AlertType.WARNING);return;}
        operando=true;atualizar();
        try{service.registrar(pendente);pendente=null;concluido=true;janela().close();}
        catch(ResultadoTransacaoIncertoException e){Alerts.showAlert("Confirmação pendente",null,"A resposta não chegou e o ajuste pode já estar salvo. Use Confirmar tentativa para conferir a mesma operação, sem descontar a perda novamente.",Alert.AlertType.WARNING);}
        catch(RuntimeException e){pendente=null;Alerts.showAlert("Ajuste não registrado",null,e.getMessage(),Alert.AlertType.WARNING);}
        finally{operando=false;atualizar();}
    }
    @FXML public void onCancelar(){if(operando||pendente!=null){avisarPendente();return;}janela().close();}
    private void avisarPendente(){Alerts.showAlert("Confirmação pendente",null,"Confirme a tentativa de ajuste antes de fechar. Se o programa for interrompido, confira o histórico antes de registrar outra perda.",Alert.AlertType.WARNING);}
    private Stage janela(){return (Stage)titulo.getScene().getWindow();}
    public boolean isConcluido(){return concluido;}
}
