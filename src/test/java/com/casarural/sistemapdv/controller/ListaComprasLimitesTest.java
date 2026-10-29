package com.casarural.sistemapdv.controller;
import com.casarural.sistemapdv.model.entities.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class ListaComprasLimitesTest {
    @Test @SuppressWarnings("unchecked") void campoModeloResumoECopiaMantemAMesmaQuantidade() throws Exception {
        JavafxTestSupport.executar(()->{
            FXMLLoader f=new FXMLLoader(ListaComprasController.class.getResource("/com/casarural/sistemapdv/view/ListaCompras.fxml"));f.load();
            Product p=new Product("1","Arroz",BigDecimal.ONE,0);p.setPrecoCusto(new BigDecimal("2.00"));
            Product q=new Product("2","Feijão",BigDecimal.ONE,0);
            List<RegraListaCompras.Item> itens=RegraListaCompras.montar(List.of(new VendaProduto(p,Integer.MAX_VALUE,1,BigDecimal.ZERO,null),new VendaProduto(q,Integer.MAX_VALUE,1,BigDecimal.ZERO,null)));
            TableView<RegraListaCompras.Item> tabela=(TableView<RegraListaCompras.Item>)f.getNamespace().get("tabela");tabela.setItems(FXCollections.observableArrayList(itens));
            TableColumn<RegraListaCompras.Item,RegraListaCompras.Item> coluna=(TableColumn<RegraListaCompras.Item,RegraListaCompras.Item>)f.getNamespace().get("colComprar");
            TableCell<RegraListaCompras.Item,RegraListaCompras.Item> cell=coluna.getCellFactory().call(coluna);
            java.lang.reflect.Method atualizar=cell.getClass().getDeclaredMethod("updateItem",RegraListaCompras.Item.class,boolean.class);atualizar.setAccessible(true);atualizar.invoke(cell,itens.get(0),false);
            TextField campo=(TextField)cell.getGraphic();assertEquals("4294967294",((Label)f.getNamespace().get("lblUnidades")).getText());
            for(String invalido:new String[]{"2147483648","99999999999999999","-1","1,5"}){campo.setText(invalido);assertEquals("2147483647",campo.getText());assertEquals(Integer.MAX_VALUE,itens.get(0).getComprar());}
            assertTrue(RegraListaCompras.texto(itens,LocalDate.of(2026,10,29)).contains("2147483647 x Arroz"));
            campo.setText("2");assertEquals(2,itens.get(0).getComprar());assertEquals("2147483649",((Label)f.getNamespace().get("lblUnidades")).getText());
            assertEquals(new BigDecimal("4.00"),itens.get(0).getCustoEstimado());
        });
    }
}
