package com.casarural.sistemapdv.services.impressao;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import static com.casarural.sistemapdv.services.impressao.ComprovanteVenda.*;

public final class SegundaVia implements Comprovante {
    private final String nome, texto;
    private SegundaVia(String nome,String texto) { this.nome=nome;this.texto=texto; }
    public String getNomeTrabalho() { return nome; }
    public String getTexto() { return texto; }
    public static SegundaVia de(Comprovante original, LocalDateTime agora) {
        List<String> l=new ArrayList<>();centralizar(l,"SEGUNDA VIA");
        escrever(l,"Reimpresso: "+agora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        separar(l);
        return new SegundaVia(original.getNomeTrabalho()+" - Segunda via",String.join("\n",l)+"\n"+original.getTexto());
    }
}
