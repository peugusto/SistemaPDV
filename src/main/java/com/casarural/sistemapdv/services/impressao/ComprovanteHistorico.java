package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.casarural.sistemapdv.services.impressao.ComprovanteVenda.*;

/** Alternativa explícita para operações anteriores ao arquivo de comprovantes. */
public final class ComprovanteHistorico implements Comprovante {
    private final String nome,texto;
    private ComprovanteHistorico(String nome,List<String> linhas) { this.nome=nome;texto=String.join("\n",linhas)+"\n"; }
    public String getNomeTrabalho() { return nome; }
    public String getTexto() { return texto; }
    private static List<String> inicio(String titulo) {
        List<String> l=new ArrayList<>();centralizar(l,"CASA RURAL");centralizar(l,titulo);
        centralizar(l,"NÃO É DOCUMENTO FISCAL");escrever(l,"Reconstruído do histórico");separar(l);return l;
    }
    private static void produtos(List<String> l,Order venda) {
        for(OrderItem i:venda.getItemPedido()) {
            escrever(l,i.getQtd()+" x "+i.getNomeProduto());
            valores(l,"  "+Money.format(i.getPrecoUnitario())+" cada",Money.format(i.getSubtotal()));
        }
    }
    public static Comprovante venda(Order v) {
        List<String> l=inicio("COMPROVANTE DE VENDA");escrever(l,"Venda: #"+v.getIdPedido());
        escrever(l,"Data: "+v.getDataPedido().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        if(v.getCostumer()!=null)escrever(l,"Cliente: "+v.getCostumer().getNomeCliente());separar(l);produtos(l,v);separar(l);
        valores(l,"Subtotal",Money.format(v.getSubtotalItens()));
        if(Money.isPositive(v.getDesconto()))valores(l,"Desconto","- "+Money.format(v.getDesconto()));
        valores(l,"TOTAL",Money.format(v.getValorTotal()));separar(l);escrever(l,"FORMAS NA VENDA ORIGINAL");
        for(PaymentMethod f:PaymentMethod.values()) {
            BigDecimal valor=v.getTotalPagamentos(f);if(Money.isPositive(valor))valores(l,nome(f),Money.format(valor));
        }
        escrever(l,"Troco da época não registrado.");separar(l);centralizar(l,"Obrigado pela preferência!");
        return new ComprovanteHistorico("Casa Rural - Venda #"+v.getIdPedido(),l);
    }
    public static Comprovante recebimento(Customer c,LocalDateTime data,PaymentMethod forma,BigDecimal pago,List<Order> compras,boolean legado) {
        List<String> l=inicio("PAGAMENTO DE FIADO");escrever(l,"Cliente: "+c.getNomeCliente());
        escrever(l,"Pagamento: "+data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        if(legado)escrever(l,"Registros antigos agrupados por data e forma de pagamento.");
        for(Order compra:compras) {
            separar(l);escrever(l,compra.getDataPedido().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" - #"+compra.getIdPedido());produtos(l,compra);
        }
        separar(l);valores(l,"TOTAL PAGO",Money.format(pago));escrever(l,"Forma: "+nome(forma));
        escrever(l,"Saldo da época não registrado.");separar(l);centralizar(l,"Obrigado pela preferência!");
        return new ComprovanteHistorico("Casa Rural - Histórico de fiado",l);
    }
}
