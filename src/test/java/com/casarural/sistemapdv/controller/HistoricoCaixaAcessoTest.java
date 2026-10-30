package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.services.AcessoRelatoriosService;
import com.casarural.sistemapdv.services.CashRegisterService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class HistoricoCaixaAcessoTest {
    static class AcessoFake extends AcessoRelatoriosService {
        int conferencias;
        long espera;
        AcessoFake() { super(null); }
        public boolean temPin() { return true; }
        public boolean confere(String pin) { conferencias++; return "4821".equals(pin); }
        public long segundosParaNovaTentativa() { return espera; }
    }
    static class CaixaFake extends CashRegisterService {
        int consultas;
        CaixaFake() { super(null); }
        public List<CashRegister> findClosed(int limite) { consultas++; return List.of(); }
    }
    static void set(Object o, String nome, Object valor) throws Exception {
        Field f = o.getClass().getDeclaredField(nome); f.setAccessible(true); f.set(o, valor);
    }
    static MainController main(AcessoFake acesso, CaixaFake caixa) throws Exception {
        MainController c = new MainController();
        Button b = new Button(); Stage dono = new Stage(); dono.setScene(new Scene(new VBox(b)));
        set(c, "botaoPDV", b); set(c, "acesso", acesso); set(c, "caixaService", caixa);
        return c;
    }
    static DialogPane dialogo() {
        return Window.getWindows().stream().filter(w -> w.getScene() != null)
                .map(w -> w.getScene().getRoot()).filter(r -> r instanceof DialogPane)
                .map(r -> (DialogPane) r).findFirst().orElseThrow();
    }
    static void responder(String pin) {
        DialogPane d = dialogo();
        if (pin != null) ((PasswordField) d.lookup(".password-field")).setText(pin);
        ButtonType t = d.getButtonTypes().stream().filter(b -> b.getButtonData() ==
                (pin == null ? ButtonBar.ButtonData.CANCEL_CLOSE : ButtonBar.ButtonData.OK_DONE)).findFirst().orElseThrow();
        ((Button) d.lookupButton(t)).fire();
    }
    @Test void cancelarPinNaoConsultaHistorico() throws Exception {
        JavafxTestSupport.executar(() -> {
            AcessoFake a = new AcessoFake(); CaixaFake caixa = new CaixaFake(); MainController c = main(a, caixa);
            Platform.runLater(() -> responder(null)); c.onHistoricoCaixaAction();
            assertEquals(0, caixa.consultas); assertEquals(0, a.conferencias);
        });
    }
    @Test void pinErradoNaoConsultaHistorico() throws Exception {
        JavafxTestSupport.executar(() -> {
            AcessoFake a = new AcessoFake(); CaixaFake caixa = new CaixaFake(); MainController c = main(a, caixa);
            Platform.runLater(() -> { responder("0000"); Platform.runLater(() -> responder(null)); });
            c.onHistoricoCaixaAction(); assertEquals(0, caixa.consultas); assertEquals(1, a.conferencias);
        });
    }
    @Test void bloqueioNaoAbreCampoDePinNemConsultaHistorico() throws Exception {
        JavafxTestSupport.executar(() -> {
            AcessoFake a = new AcessoFake(); a.espera = 30;
            CaixaFake caixa = new CaixaFake(); MainController c = main(a, caixa);
            Platform.runLater(() -> {
                DialogPane d = dialogo(); assertNull(d.lookup(".password-field"));
                assertTrue(d.getContentText().contains("30")); ((Button) d.lookupButton(ButtonType.OK)).fire();
            });
            c.onHistoricoCaixaAction(); assertEquals(0, caixa.consultas); assertEquals(0, a.conferencias);
        });
    }
    @Test void pinCorretoConsultaHistoricoDepoisDaAutorizacao() throws Exception {
        JavafxTestSupport.executar(() -> {
            AcessoFake a = new AcessoFake(); CaixaFake caixa = new CaixaFake(); MainController c = main(a, caixa);
            Platform.runLater(() -> {
                assertEquals(0, caixa.consultas); responder("4821");
                Platform.runLater(() -> {
                    assertEquals(1, caixa.consultas);
                    for (Window w : new ArrayList<>(Window.getWindows()))
                        if (w instanceof Stage && ((Stage) w).getTitle().equals("Histórico de caixas")) w.hide();
                });
            });
            c.onHistoricoCaixaAction(); assertEquals(1, caixa.consultas); assertEquals(1, a.conferencias);
        });
    }
}
