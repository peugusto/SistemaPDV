package com.casarural.sistemapdv.controller;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.TurnoPao;
import com.casarural.sistemapdv.services.PaoService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class PaoControllerTest {
    static class Servico extends PaoService {
        LocalDate consultado, registrado;
        int consultas;
        Servico(){super(null,null,null);}
        public Optional<Product> produtoPao(){ return Optional.of(new Product("1","Pão", java.math.BigDecimal.ONE,0)); }
        public List<DiaPao> historico(Product p, LocalDate hoje){consultado=hoje;consultas++;return List.of(new DiaPao(hoje));}
        public void registrarCompra(Product p,LocalDate dia,TurnoPao t,int qtd){registrado=dia;}
    }
    static class Tela {
        FXMLLoader f = new FXMLLoader(PaoController.class.getResource("/com/casarural/sistemapdv/view/PaoDia.fxml"));
        PaoController c; Servico s=new Servico();
        Tela() throws Exception {Parent root=f.load();Stage w=new Stage();w.setScene(new Scene(root)); c=f.getController(); hora("2026-10-29T14:59:00");c.setService(s);}
        void hora(String t){ c.setClock(Clock.fixed(LocalDateTime.parse(t).atZone(ZoneId.of("America/Sao_Paulo")).toInstant(),ZoneId.of("America/Sao_Paulo"))); }
        Label label(String id){return (Label) f.getNamespace().get(id);}
        TextField campo(String id){return (TextField) f.getNamespace().get(id);}
        DatePicker data(){return (DatePicker) f.getNamespace().get("dataDia");}
    }
    @Test void mudaTurnoSemConsultarBancoNemApagarDigitacao() throws Exception {JavafxTestSupport.executar(()->{
        Tela t=new Tela(); t.campo("txtCompraManha").setText("123");int n=t.s.consultas;
        assertEquals("Tem agora",t.label("lblSobraManhaRotulo").getText());
        t.hora("2026-10-29T15:01:00"); t.c.atualizarHorario();
        assertEquals("Sobrou",t.label("lblSobraManhaRotulo").getText());
        assertEquals("123",t.campo("txtCompraManha").getText());assertEquals(n,t.s.consultas);
        t.hora("2026-10-29T19:01:00");t.c.atualizarHorario();assertEquals("Sobrou",t.label("lblSobraTardeRotulo").getText());
    });}
    @Test void meiaNoitePermiteNovoDiaEPreservaDataEscolhida() throws Exception {JavafxTestSupport.executar(()->{
        Tela t=new Tela(); t.data().setValue(LocalDate.of(2026,10,28));
        t.hora("2026-10-30T00:01:00");t.c.atualizarHorario();assertEquals(LocalDate.of(2026,10,28),t.data().getValue());
        t.data().setValue(LocalDate.of(2026,10,30));t.campo("txtCompraManha").setText("10");t.c.onRegistrarManha();
        assertEquals(LocalDate.of(2026,10,30),t.s.registrado);assertEquals(t.s.registrado,t.s.consultado);
    });}
}
