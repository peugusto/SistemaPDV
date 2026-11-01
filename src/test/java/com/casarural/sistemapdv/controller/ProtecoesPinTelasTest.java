package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.*;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.services.*;
import com.casarural.sistemapdv.util.Money;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class ProtecoesPinTelasTest {
    static void set(Object o,String nome,Object v)throws Exception {Class<?> tipo=o.getClass();while(tipo!=null){try{Field f=tipo.getDeclaredField(nome);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){tipo=tipo.getSuperclass();}}throw new AssertionError(nome);}
    static class Gate extends ProtecaoPin {boolean permite;int chamadas;String acao;public boolean autorizar(Window w,String a){chamadas++;acao=a;return permite;}}
    static class Acesso extends AcessoRelatoriosService {
        int erros,acertos;Acesso(){super(null);}public boolean temPin(){return true;}public long segundosParaNovaTentativa(){return 0;}
        public boolean confere(String pin){if("4821".equals(pin)){acertos++;return true;}erros++;return false;}
    }
    static class Produtos extends ProductService {
        int salvos,entradas,excluidos,desativados;Product salvo;EntradaEstoque entrada;boolean historico,incerto;
        Produtos(){super(null);}public void saveOrUpdate(Product p){salvos++;salvo=p;p.setIdProduto(7);}
        public void registrarEntrada(EntradaEstoque e){entradas++;entrada=e;if(incerto){incerto=false;throw new ResultadoTransacaoIncertoException("entrada",new java.sql.SQLException("Resposta perdida"));}}
        public boolean temHistorico(int id){return historico;}public void delete(Integer id){excluidos++;}public void desativar(Product p){desativados++;p.setAtivo(false);}
        public List<Product> findAll(){return List.of();}
    }
    static class Clientes extends CustomerService {
        int inseridos,alterados;Customer salvo;Clientes(){super(null);}public void insert(Customer c){inseridos++;salvo=c;}
        public void saveOrUpdate(Customer c){alterados++;salvo=c;}
    }
    static class Caixa extends CashRegisterService {
        int sangrias,reforcos,aberturas;Caixa(){super(null);}public CashSummary summarize(CashRegister c){return new CashSummary(new BigDecimal("100"));}
        public void addSangria(CashRegister c,BigDecimal valor,String motivo){sangrias++;}
        public void addReforco(CashRegister c,BigDecimal valor,String motivo){reforcos++;}
        public CashRegister open(BigDecimal v){aberturas++;return new CashRegister();}
    }
    static FXMLLoader tela(String nome)throws Exception {
        FXMLLoader l=new FXMLLoader(ProtecoesPinTelasTest.class.getResource("/com/casarural/sistemapdv/view/"+nome+".fxml"));
        Parent p=l.load();Stage s=new Stage();s.setScene(new Scene(p));return l;
    }
    static TextField txt(FXMLLoader l,String nome){return (TextField)l.getNamespace().get(nome);}
    static Button btn(FXMLLoader l,String nome){return (Button)l.getNamespace().get(nome);}
    static void salvar(FXMLLoader l,ProductFormController c){Button b=btn(l,"btnSave");c.onBtnSaveAction(new ActionEvent(b,b));}
    static void salvar(FXMLLoader l,CustomerFormController c){Button b=btn(l,"btnSave");c.onBtnSaveAction(new ActionEvent(b,b));}
    static Product produto(){Product p=new Product("1","Arroz",Money.parse("10"),5);p.setIdProduto(7);p.setPrecoCusto(Money.parse("4"));p.registrarEstadoOriginal();return p;}
    static Customer cliente(){Customer c=new Customer("Maria",Money.parse("100"),CustomerStatus.DISPONIVEL);c.setIdCliente(9);c.registrarEstadoOriginal();return c;}
    static FXMLLoader editarProduto(Product original,Produtos service)throws Exception {
        FXMLLoader l=tela("ProductForm");ProductFormController c=l.getController();c.setProduct(original);
        c.setServices(service,new ProductListController(){public void updateTableView(){}});c.updateFormData();return l;
    }
    static FXMLLoader editarCliente(Customer original,Clientes service)throws Exception {
        FXMLLoader l=tela("CustomerForm");CustomerFormController c=l.getController();c.setCustomer(original);
        c.setServices(service,new CustomerListController(){public void updateTableView(){}});c.updateFormData();return l;
    }
    static FXMLLoader entrada(Produtos service,Product original)throws Exception {
        FXMLLoader l=tela("CadastroProduto");CadastroProdutosController c=l.getController();c.setProductService(service);c.setProduct(original);set(c,"isExistingProduct",true);
        txt(l,"txtCodBarras").setText("1");txt(l,"txtEstoque").setText("2");txt(l,"txtCusto").setText("4");return l;
    }
    static DialogPane dialogo(){return Window.getWindows().stream().filter(w->w.getScene()!=null).map(w->w.getScene().getRoot()).filter(p->p instanceof DialogPane).map(p->(DialogPane)p).findFirst().orElseThrow();}
    static void responderPin(String pin){DialogPane d=dialogo();if(pin!=null)((PasswordField)d.lookup(".password-field")).setText(pin);ButtonBar.ButtonData tipo=pin==null?ButtonBar.ButtonData.CANCEL_CLOSE:ButtonBar.ButtonData.OK_DONE;ButtonType b=d.getButtonTypes().stream().filter(t->t.getButtonData()==tipo).findFirst().orElseThrow();((Button)d.lookupButton(b)).fire();}
    @Test void precoAlteradoPinErradoECancelarNaoGravamNemMutamProduto()throws Exception {JavafxTestSupport.executar(()->{
        Produtos p=new Produtos();Product original=produto();FXMLLoader l=editarProduto(original,p);ProductFormController c=l.getController();Acesso a=new Acesso();set(c,"protecao",new ProtecaoPin(a));txt(l,"txtPreco").setText("20");
        Platform.runLater(()->{responderPin("0000");Platform.runLater(()->responderPin(null));});salvar(l,c);
        assertEquals(0,p.salvos);assertEquals(Money.parse("10"),original.getPrecoProduto());assertEquals("20",txt(l,"txtPreco").getText());assertEquals(1,a.erros);assertEquals(0,a.acertos);
    });}
    @Test void pinCorretoAutorizaMudancaDeFiadoUmaVez()throws Exception {JavafxTestSupport.executar(()->{
        Produtos p=new Produtos();Product original=produto();FXMLLoader l=editarProduto(original,p);ProductFormController c=l.getController();Acesso a=new Acesso();set(c,"protecao",new ProtecaoPin(a));((CheckBox)l.getNamespace().get("chkPermiteFiado")).setSelected(false);
        Platform.runLater(()->responderPin("4821"));salvar(l,c);salvar(l,c);assertEquals(1,p.salvos);assertFalse(p.salvo.isPermiteFiado());assertEquals(1,a.acertos);
    });}
    @Test void nomeDoProdutoPodeSerCorrigidoSemPin()throws Exception {JavafxTestSupport.executar(()->{
        Produtos p=new Produtos();FXMLLoader l=editarProduto(produto(),p);ProductFormController c=l.getController();Gate g=new Gate();set(c,"protecao",g);txt(l,"txtNome").setText("Arroz corrigido");salvar(l,c);assertEquals(1,p.salvos);assertEquals(0,g.chamadas);
    });}
    @Test void estoqueCustoAtivacaoTipoEControleNaoPassamSemPin()throws Exception {JavafxTestSupport.executar(()->{
        for(String campo:List.of("txtCodBarras","txtEstoque","txtCusto","chkAtivo","chkPesavel","chkControlaEstoque")) {
            Produtos p=new Produtos();Product original=produto();FXMLLoader l=editarProduto(original,p);ProductFormController c=l.getController();Gate g=new Gate();set(c,"protecao",g);
            if(campo.startsWith("txt"))txt(l,campo).setText("8");else {CheckBox b=(CheckBox)l.getNamespace().get(campo);b.setSelected(!b.isSelected());}
            salvar(l,c);assertEquals(1,g.chamadas,campo);assertEquals(0,p.salvos,campo);assertEquals(5,original.getEstoque());assertEquals(Money.parse("4"),original.getPrecoCusto());
        }
    });}
    @Test void cadastroNovoDeProdutoTambemExigePin()throws Exception {JavafxTestSupport.executar(()->{
        Produtos p=new Produtos();FXMLLoader l=tela("CadastroProduto");CadastroProdutosController c=l.getController();Product original=new Product();c.setProduct(original);c.setProductService(p);Gate g=new Gate();set(c,"protecao",g);
        txt(l,"txtCodBarras").setText("2");txt(l,"txtNome").setText("Feijão");txt(l,"txtPreco").setText("10");txt(l,"txtEstoque").setText("5");c.onBotaoSalvarAction();
        assertEquals(1,g.chamadas);assertEquals(0,p.salvos);assertEquals(0,original.getIdProduto());assertNull(original.getNomeProduto());
    });}
    @Test void entradaSemAlterarCustoContinuaLivre()throws Exception {JavafxTestSupport.executar(()->{
        Produtos p=new Produtos();FXMLLoader l=entrada(p,produto());CadastroProdutosController c=l.getController();Gate g=new Gate();set(c,"protecao",g);c.onBotaoSalvarAction();assertEquals(1,p.entradas);assertEquals(0,g.chamadas);assertNull(p.entrada.getCustoInformado());
    });}
    @Test void custoNovoNaEntradaRecusadoNaoRegistraQuantidade()throws Exception {JavafxTestSupport.executar(()->{
        Produtos p=new Produtos();Product original=produto();FXMLLoader l=entrada(p,original);CadastroProdutosController c=l.getController();Gate g=new Gate();set(c,"protecao",g);txt(l,"txtCusto").setText("5");c.onBotaoSalvarAction();assertEquals(0,p.entradas);assertEquals(1,g.chamadas);assertEquals(5,original.getEstoque());assertEquals(Money.parse("4"),original.getPrecoCusto());
    });}
    @Test void confirmacaoDeEntradaIncertaReutilizaSomenteDadosJaAutorizados()throws Exception {JavafxTestSupport.executar(()->{
        Produtos p=new Produtos();p.incerto=true;FXMLLoader l=entrada(p,produto());CadastroProdutosController c=l.getController();Gate g=new Gate();g.permite=true;set(c,"protecao",g);txt(l,"txtCusto").setText("5");
        c.onBotaoSalvarAction();EntradaEstoque primeira=p.entrada;c.onBotaoSalvarAction();assertSame(primeira,p.entrada);assertEquals(2,p.entradas);assertEquals(1,g.chamadas);
    });}
    @Test void limiteEDesbloqueioRecusadosNaoMudamCliente()throws Exception {JavafxTestSupport.executar(()->{
        for(boolean desbloquear:new boolean[]{false,true}) {
            Clientes s=new Clientes();Customer original=cliente();if(desbloquear)original.setSituacaoFiado(CustomerStatus.BLOQUEADO);
            FXMLLoader l=editarCliente(original,s);CustomerFormController c=l.getController();Gate g=new Gate();set(c,"protecao",g);
            if(desbloquear)((ComboBox<CustomerStatus>)l.getNamespace().get("comboBoxStatus")).setValue(CustomerStatus.DISPONIVEL);else txt(l,"txtLimite").setText("200");
            salvar(l,c);assertEquals(0,s.alterados);assertEquals(1,g.chamadas);assertEquals(Money.parse("100"),original.getLimiteCredito());assertEquals(desbloquear?CustomerStatus.BLOQUEADO:CustomerStatus.DISPONIVEL,original.getSituacaoFiado());
        }
    });}
    @Test void contatoDoClienteSemAlteracaoDeCreditoContinuaLivre()throws Exception {JavafxTestSupport.executar(()->{
        Clientes s=new Clientes();FXMLLoader l=editarCliente(cliente(),s);CustomerFormController c=l.getController();Gate g=new Gate();set(c,"protecao",g);txt(l,"txtNome").setText("Maria corrigida");salvar(l,c);assertEquals(1,s.alterados);assertEquals(0,g.chamadas);
    });}
    @Test void clienteNovoComLimitePositivoPrecisaDePinEComZeroNao()throws Exception {
        for(String limite:List.of("100","0"))JavafxTestSupport.executar(()->{
            Clientes s=new Clientes();FXMLLoader l=tela("RegisterCustomer");RegisterCustomerController c=l.getController();Customer original=new Customer();c.setCustomer(original);c.setCustomerService(s);Gate g=new Gate();set(c,"protecao",g);
            txt(l,"txtNome").setText("Maria");txt(l,"txtLimite").setText(limite);c.onBtnSaveAction();assertEquals(limite.equals("0")?1:0,s.inseridos);assertEquals(limite.equals("0")?0:1,g.chamadas);assertNull(original.getNomeCliente());
        });
    }
    @Test void sangriaCanceladaNaoRetiraEPinCorretoRetira()throws Exception {
        for(boolean permitir:new boolean[]{false,true})JavafxTestSupport.executar(()->{
            Caixa s=new Caixa();FXMLLoader l=tela("CaixaValor");CaixaValorController c=l.getController();CashRegister caixa=new CashRegister();caixa.setIdCaixa(1);caixa.setAbertoEm(LocalDateTime.now());c.configurar(CaixaValorController.Modo.SANGRIA,s,caixa);
            Acesso a=new Acesso();set(c,"protecao",new ProtecaoPin(a));txt(l,"campoValor").setText("20");txt(l,"campoMotivo").setText("Cofre");Platform.runLater(()->responderPin(permitir?"4821":null));c.onConfirmar();assertEquals(permitir?1:0,s.sangrias);assertEquals(permitir,c.isConcluido());
        });
    }
    @Test void aberturaEReforcoContinuamSemPin()throws Exception {
        for(CaixaValorController.Modo modo:List.of(CaixaValorController.Modo.ABRIR,CaixaValorController.Modo.REFORCO))JavafxTestSupport.executar(()->{
            Caixa s=new Caixa();FXMLLoader l=tela("CaixaValor");CaixaValorController c=l.getController();CashRegister caixa=new CashRegister();caixa.setAbertoEm(LocalDateTime.now());c.configurar(modo,s,caixa);Gate g=new Gate();set(c,"protecao",g);txt(l,"campoValor").setText("20");c.onConfirmar();assertEquals(0,g.chamadas);assertEquals(1,s.aberturas+s.reforcos);
        });
    }
    @Test void exclusaoEDesativacaoRecusadasNaoChamamEscrita()throws Exception {
        for(boolean historico:new boolean[]{false,true})JavafxTestSupport.executar(()->{
            Produtos s=new Produtos();s.historico=historico;FXMLLoader l=tela("product_list");ProductListController c=l.getController();c.setProductService(s);Gate g=new Gate();set(c,"protecao",g);
            Platform.runLater(()->{DialogPane d=dialogo();ButtonType b=d.getButtonTypes().stream().filter(t->t.getButtonData()==(historico?ButtonBar.ButtonData.OTHER:ButtonBar.ButtonData.OK_DONE)).findFirst().orElseThrow();((Button)d.lookupButton(b)).fire();});
            Method excluir=ProductListController.class.getDeclaredMethod("onDeleteAction",Product.class);excluir.setAccessible(true);excluir.invoke(c,produto());assertEquals(1,g.chamadas);assertEquals(0,s.excluidos+s.desativados);
        });
    }
}
