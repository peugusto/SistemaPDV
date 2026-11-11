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

    @Test void mostraCoberturaDoValorMovimentadoQuandoHaDevolucaoEVendaSemCusto() throws Exception {
        JavafxTestSupport.executar(() -> {
            ResumoVendas resumo = new ResumoVendas();
            resumo.adicionarCustosDoPedido(new BigDecimal("-100"), new BigDecimal("-100"),
                    new BigDecimal("-60"), BigDecimal.ZERO, true, false);
            resumo.adicionarCustosDoPedido(new BigDecimal("200"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, false, true);
            RelatoriosController controller = new RelatoriosController();
            Label lucro = new Label(), detalhe = new Label();
            campo(controller, "lblLucro", lucro);
            campo(controller, "lblLucroDetalhe", detalhe);
            controller.mostrarLucro(resumo);
            assertEquals(com.casarural.sistemapdv.util.Money.format(new BigDecimal("-40")), lucro.getText());
            assertTrue(detalhe.getText().contains("custo parcial"));
            assertTrue(detalhe.getText().contains("33,3% do valor movimentado"));
        });
    }

    private static void campo(RelatoriosController controller, String nome, Label valor) throws Exception {
        java.lang.reflect.Field campo = RelatoriosController.class.getDeclaredField(nome);
        campo.setAccessible(true);
        campo.set(controller, valor);
    }
}
