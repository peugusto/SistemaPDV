package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ListaComprasImpressaoTest {
    static final LocalDateTime DATA=LocalDateTime.of(2026,10,29,20,0);
    static Item item(String nome,int estoque,int pendente) {
        return RegraListaCompras.acumulado(new Product(nome,nome,Money.parse("10"),estoque),pendente);
    }
    @Test void usaPendenciaSemSubtrairEstoqueNovamente() {
        Item i=item("Arroz",500,3); assertEquals(3,i.getComprar()); assertEquals(3,i.getPendente());
        i.setComprar(8); assertEquals(3,i.getPendente());
    }
    @Test void acabadosVemPrimeiroETotalExcluiQuantidadeZero() {
        Item arroz=item("Arroz",10,3), cafe=item("Café",0,5), sal=item("Sal",0,2); sal.setComprar(0);
        String t=ComprovanteListaCompras.gerar(List.of(arroz,sal,cafe),DATA).getTexto();
        assertTrue(t.indexOf("[ ] 5 x Café")<t.indexOf("[ ] 3 x Arroz")); assertFalse(t.contains("Sal"));
        assertTrue(t.contains("ACABARAM")); assertTrue(t.contains("REPOR")); assertTrue(t.contains("29/10/2026 20:00"));
        assertTrue(t.lines().anyMatch(l->l.startsWith("PRODUTOS")&&l.endsWith("2")));
        assertTrue(t.lines().anyMatch(l->l.startsWith("UNIDADES PARA COMPRAR")&&l.endsWith("8")));
    }
    @Test void imprimirNaoAlteraQuantidadeENemDependeDeEstoqueTerCaidoNaSemana() {
        Item i=item("Arroz",8,4); String t=ComprovanteListaCompras.gerar(List.of(i),DATA).getTexto();
        assertTrue(t.contains("[ ] 4 x Arroz")); assertEquals(4,i.getComprar()); assertEquals(4,i.getPendente());
    }
    @Test void vazioOuTudoZeradoNaoEnviaTrabalhoVazio() {
        assertThrows(IllegalArgumentException.class,()->ComprovanteListaCompras.gerar(List.of(),DATA));
        assertThrows(IllegalArgumentException.class,()->ComprovanteListaCompras.gerar(List.of(item("Sal",0,0)),DATA));
    }
    @Test void filaCapturaEscolhasAntesDeOutrasEdicoes() {
        List<Runnable> fila=new ArrayList<>(); List<Comprovante> enviados=new ArrayList<>(); Item i=item("Arroz",5,3);
        ImpressaoVendaService s=new ImpressaoVendaService(enviados::add,fila::add);
        var f=s.emitirListaCompras(List.of(i),DATA); i.setComprar(99); fila.get(0).run(); f.join();
        assertTrue(enviados.get(0).getTexto().contains("[ ] 3 x Arroz")); assertFalse(enviados.get(0).getTexto().contains("99 x"));
    }
    @Test void nomesLongosEQuantidadeExtremaNaoPerdemTextoNemTransbordamTotal() {
        Item a=item("PRODUTO MUITO LONGO ".repeat(5),0,Integer.MAX_VALUE), b=item("Café",0,Integer.MAX_VALUE);
        String t=ComprovanteListaCompras.gerar(List.of(a,b),DATA).getTexto();
        assertTrue(t.contains("4294967294")); assertTrue(t.lines().allMatch(l->l.length()<=36));
        String[] linhas=t.split("\n");
        assertEquals(List.of(linhas),ImpressoraTermica.paginar(linhas,7).stream().flatMap(List::stream).collect(java.util.stream.Collectors.toList()));
    }
    @Test void copiaMantemPrioridadeDosProdutosAcabados() {
        String t=RegraListaCompras.texto(List.of(item("Arroz",10,3),item("Café",0,5)),DATA.toLocalDate());
        assertTrue(t.indexOf("5 x Café")<t.indexOf("3 x Arroz"));
    }
    @Test void itemDaListaComNomeLongoContinuaNaMesmaEtiquetaQuandoCabe() {
        String[] linhas={"CABECALHO", "DATA", "REPOR", "[ ] 12 x Nome longo", "continuação do nome", "------------------------------------"};
        List<List<String>> paginas=ImpressoraTermica.paginar(linhas,4);
        assertEquals(List.of("CABECALHO","DATA","REPOR"),paginas.get(0));
        assertEquals("[ ] 12 x Nome longo",paginas.get(1).get(0));assertEquals("continuação do nome",paginas.get(1).get(1));
    }
}
