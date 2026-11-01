package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class AcoesProtegidasTest {
    static Product produto(){Product p=new Product("1","Arroz",new BigDecimal("10"),5);p.setIdProduto(7);p.setPrecoCusto(new BigDecimal("4"));return p;}
    static Customer cliente(){Customer c=new Customer("Maria",new BigDecimal("100"),CustomerStatus.DISPONIVEL);c.setIdCliente(9);return c;}
    @Test void precoCustoEstoqueFiadoAtivoETipoPrecisamDeAutorizacao() {
        List<Consumer<Product>> mudancas=List.of(p->p.setCodBarras("OUTRO"),p->p.setPrecoProduto(new BigDecimal("9")),p->p.setPrecoCusto(null),
                p->p.setEstoque(8),p->p.setPermiteFiado(false),p->p.setAtivo(false),p->p.setControlaEstoque(false),p->p.setTipoVenda(TipoVenda.PESAVEL));
        for(Consumer<Product> mudar:mudancas){Product antes=produto(),depois=produto();mudar.accept(depois);assertFalse(AcoesProtegidas.produto(antes,depois).isEmpty());}
    }
    @Test void nomeOuFormatacaoEquivalenteNaoPedemPin() {
        Product a=produto(),b=produto();b.setNomeProduto("Outro nome");b.setCodBarras(" 1 ");b.setPrecoProduto(new BigDecimal("10.00"));b.setPrecoCusto(new BigDecimal("4.00"));
        assertTrue(AcoesProtegidas.produto(a,b).isEmpty());
    }
    @Test void cadastrarOuReativarProdutoNaoContornaProtecao() {
        Product novo=produto();novo.setIdProduto(0);assertFalse(AcoesProtegidas.produto(novo,produto()).isEmpty());
        Product inativo=produto();inativo.setAtivo(false);assertFalse(AcoesProtegidas.produto(inativo,produto()).isEmpty());
    }
    @Test void aumentarReduzirOuConcederLimiteInicialPedemPin() {
        for(String limite:new String[]{"50","150"}) {Customer a=cliente(),b=cliente();b.setLimiteCredito(new BigDecimal(limite));assertFalse(AcoesProtegidas.cliente(a,b).isEmpty());}
        Customer novo=cliente();novo.setIdCliente(0);assertFalse(AcoesProtegidas.cliente(null,novo).isEmpty());novo.setLimiteCredito(BigDecimal.ZERO);assertTrue(AcoesProtegidas.cliente(null,novo).isEmpty());
    }
    @Test void desbloquearPedePinEBloquearOuEditarContatoNaoPede() {
        Customer bloqueado=cliente();bloqueado.setSituacaoFiado(CustomerStatus.BLOQUEADO);
        assertFalse(AcoesProtegidas.cliente(bloqueado,cliente()).isEmpty());assertTrue(AcoesProtegidas.cliente(cliente(),bloqueado).isEmpty());
        Customer contato=cliente();contato.setNomeCliente("Nome corrigido");contato.setTelefone("11912345678");assertTrue(AcoesProtegidas.cliente(cliente(),contato).isEmpty());
    }
    @Test void copiaDeClientePreservaBaselineESemMutarOriginal() {
        Customer original=cliente();original.registrarEstadoOriginal();Customer copia=cliente();copia.copiarEstadoOriginal(original);copia.setLimiteCredito(new BigDecimal("200"));
        assertEquals(new BigDecimal("100.00"),original.getLimiteCredito());assertEquals(original.getLimiteCredito(),copia.getEstadoOriginal()[0]);
        Object[] fora=copia.getEstadoOriginal();fora[0]=BigDecimal.ZERO;assertEquals(original.getLimiteCredito(),copia.getEstadoOriginal()[0]);
    }
}
