package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.util.BancoEmSegundoPlano;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class BancoEmSegundoPlanoTest {
    @Test void servidorQueAceitaMasNaoRespondeTemPrazoSemPararEventosDaTela() throws Exception {
        java.util.concurrent.atomic.AtomicReference<java.net.Socket> conectado=new java.util.concurrent.atomic.AtomicReference<>();
        try(java.net.ServerSocket servidor=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
            Thread aceitar=new Thread(()->{try{java.net.Socket s=servidor.accept();conectado.set(s);while(!s.isClosed())Thread.sleep(50);}catch(Exception e){/* servidor sintetico encerrado */}});
            aceitar.setDaemon(true);aceitar.start();
            try {JavafxTestSupport.executar(()->{
                AtomicInteger eventos=new AtomicInteger();javafx.animation.Timeline pulso=new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(50),e->eventos.incrementAndGet()));
                pulso.setCycleCount(javafx.animation.Animation.INDEFINITE);pulso.play();
                long inicio=System.nanoTime();
                try {
                    assertThrows(SQLException.class,()->BancoEmSegundoPlano.executar(()->java.sql.DriverManager.getConnection(
                            com.casarural.sistemapdv.db.PrazosBanco.url("jdbc:mysql://127.0.0.1:"+servidor.getLocalPort()+"/SINTETICO?socketTimeout=0&connectTimeout=0",false),"FICTICIO","FICTICIO")));
                    long ms=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inicio);
                    assertTrue(ms<18000,"Conexao sem resposta excedeu o prazo: "+ms);assertTrue(eventos.get()>10,"A tela parou de processar eventos");
                } finally {pulso.stop();}
            });} finally {if(conectado.get()!=null)conectado.get().close();}
        }
    }
    @Test void telaProcessaEventosMasBloqueiaCliquesEnquantoDaoTrabalha() throws Exception {JavafxTestSupport.executar(()->{
        Button botao=new Button("Salvar");AtomicInteger cliques=new AtomicInteger(),eventos=new AtomicInteger();botao.setOnAction(e->cliques.incrementAndGet());
        Stage stage=new Stage();stage.setScene(new Scene(new VBox(botao)));stage.show();
        Callable<String> dao=BancoEmSegundoPlano.proteger(Callable.class,(Callable<String>)()->{
            assertFalse(Platform.isFxApplicationThread());
            Platform.runLater(()->{assertTrue(botao.isDisabled());botao.fire();eventos.incrementAndGet();});
            Thread.sleep(250);return "confirmado";
        });
        assertEquals("confirmado",dao.call());assertEquals(1,eventos.get());assertEquals(0,cliques.get());assertFalse(botao.isDisabled());
        botao.fire();assertEquals(1,cliques.get());
    });}
    @Test void falhaMantemTipoOriginalERestauraJanela() throws Exception {JavafxTestSupport.executar(()->{
        Button b=new Button();Stage s=new Stage();s.setScene(new Scene(new VBox(b)));s.show();SQLException original=new SQLException("SINTETICO");
        Callable<Object> dao=BancoEmSegundoPlano.proteger(Callable.class,(Callable<Object>)()->{throw original;});
        assertSame(original,assertThrows(SQLException.class,dao::call));assertFalse(b.isDisabled());
    });}
    @Test void chamadaReentranteNaoIniciaOutraEscrita() throws Exception {JavafxTestSupport.executar(()->{
        AtomicInteger rejeicoes=new AtomicInteger(),escritas=new AtomicInteger();
        BancoEmSegundoPlano.executar(()->{
            Platform.runLater(()->{assertThrows(DbException.class,()->BancoEmSegundoPlano.executar(()->{escritas.incrementAndGet();return null;}));rejeicoes.incrementAndGet();});
            Thread.sleep(200);return null;
        });
        assertEquals(1,rejeicoes.get());assertEquals(0,escritas.get());
    });}
}
