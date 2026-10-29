package com.casarural.sistemapdv.controller;
import com.casarural.sistemapdv.model.entities.ResumoVendas;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(JavafxTestSupport.class)
class RelatoriosControllerTest {
    @Test void apresentaLucroParcialMesmoQuandoPercentualArredondaParaZeroOuCem() throws Exception {
        JavafxTestSupport.executar(()->{
            for(String conhecido : new String[]{"4", "996", "0.01", "999.99"}) {
                ResumoVendas r=new ResumoVendas();r.adicionarCustosDoPedido(new BigDecimal("1000"),new BigDecimal(conhecido),BigDecimal.ZERO,BigDecimal.ZERO);
                RelatoriosController c=new RelatoriosController();Label lucro=new Label(), detalhe=new Label();
                for(String nome:new String[]{"lblLucro","lblLucroDetalhe"}) {java.lang.reflect.Field f=RelatoriosController.class.getDeclaredField(nome);f.setAccessible(true);f.set(c,nome.equals("lblLucro")?lucro:detalhe);}
                c.mostrarLucro(r);assertNotEquals("—",lucro.getText());assertTrue(detalhe.getText().contains("custo parcial"));
            }
        });
    }
}
