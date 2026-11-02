package com.casarural.sistemapdv.services.impressao;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DestinoProcessoTest {
    static Comprovante documento(String texto){return new Comprovante(){public String getNomeTrabalho(){return "FICTICIO";}public String getTexto(){return texto;}};}
    static DestinoProcesso destino(String modo,AtomicReference<Process> ref,long timeout){return new DestinoProcesso(()->{
        Process p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),Auxiliar.class.getName(),modo)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();ref.set(p);return p;
    },timeout);}
    /** Processo real de teste que nunca procura uma impressora. */
    public static class Auxiliar {
        public static void main(String[] a)throws Exception {
            if(!a[0].equals("nao-ler"))ImprimirProcesso.ler(System.in);
            if(a[0].equals("falhar"))System.exit(1);
            if(a[0].equals("travar")||a[0].equals("nao-ler"))for(;;)try{Thread.sleep(1000);}catch(InterruptedException e){/* simula chamada nativa que ignora interrupcao */}
        }
    }
    @Test void processoTravadoEncerraENovoEnvioPodeConcluir()throws Exception {
        AtomicReference<Process> p=new AtomicReference<>();long inicio=System.nanoTime();
        assertThrows(EnvioImpressaoIncertoException.class,()->destino("travar",p,1200).enviar(documento("PRODUTO FICTICIO")));
        assertFalse(p.get().isAlive());assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inicio)<5000);
        destino("ok",p,4000).enviar(documento("PROXIMO PRODUTO FICTICIO"));assertEquals(0,p.get().exitValue());
    }
    @Test void filhoQueNaoLeEntradaTambemTemPrazo()throws Exception {
        AtomicReference<Process> p=new AtomicReference<>();
        assertThrows(EnvioImpressaoIncertoException.class,()->destino("nao-ler",p,1200).enviar(documento("X".repeat(1_900_000))));assertFalse(p.get().isAlive());
    }
    @Test void erroDoFilhoNaoTentaImprimirOutraVez()throws Exception {
        AtomicReference<Process> p=new AtomicReference<>();
        assertThrows(EnvioImpressaoIncertoException.class,()->destino("falhar",p,4000).enviar(documento("FICTICIO")));assertEquals(1,p.get().exitValue());
    }
    @Test void protocoloPreservaAcentosERecusaTamanhoForjado()throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImprimirProcesso.gravar(bytes,documento("Pão e café\nR$ 10,00"));
        assertEquals("Pão e café\nR$ 10,00",ImprimirProcesso.ler(new ByteArrayInputStream(bytes.toByteArray())).getTexto());
        bytes.reset();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(1);out.writeUTF("FICTICIO");out.writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class,()->ImprimirProcesso.ler(new ByteArrayInputStream(bytes.toByteArray())));
    }
}
