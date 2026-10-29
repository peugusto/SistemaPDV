package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.EntradaEstoque;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceValidacaoTest {
    private static Product produto(int id, int estoque) {
        Product p = new Product("900001", "Arroz", Money.parse("8"), estoque);
        p.setIdProduto(id);
        return p;
    }

    private static class Produtos implements ProductDao {
        final Product registro = produto(1, 9);
        int consultas;
        int insercoes;
        int atualizacoes;
        int desativacoes;
        int ultimoIdDesativado;
        Product salvo;
        boolean falharAntes;
        boolean falharDepois;
        Runnable antesDeDesativar = () -> {};

        int chamadas() { return consultas + insercoes + atualizacoes + desativacoes; }
        public Optional<Product> findByCodBarras(String codigo) { consultas++; return Optional.empty(); }
        public void insert(Product p) { insercoes++; salvo = p; }
        public void update(Product p) { atualizacoes++; salvo = p; }
        public void desativarPorId(int id) {
            desativacoes++;
            ultimoIdDesativado = id;
            antesDeDesativar.run();
            if (falharAntes) {
                falharAntes = false;
                throw new DbException("Falha antes da escrita, rollback confirmado.");
            }
            registro.setAtivo(false);
            if (falharDepois) {
                falharDepois = false;
                throw new ResultadoTransacaoIncertoException("desativação do produto", new SQLException("Resposta perdida."));
            }
        }
        public int contarUsos(int id) { throw new AssertionError("Consulta não esperada."); }
        public Optional<Product> findById(Integer id) { throw new AssertionError("Consulta não esperada."); }
        public List<Product> findAll() { throw new AssertionError("Consulta não esperada."); }
        public void registrarEntrada(EntradaEstoque entrada) { throw new AssertionError("Entrada não esperada."); }
        public void deleteById(Integer id) { throw new AssertionError("Exclusão não esperada."); }
        public Product instantiate(ResultSet rs) { throw new AssertionError("JDBC não esperado."); }
    }

    @Test
    void estoqueNegativoRecusadoAntesDeQualquerConsultaOuEscrita() {
        for (int id : new int[]{0, 1}) {
            Produtos dao = new Produtos();
            Product p = produto(id, -1);
            DbException e = assertThrows(DbException.class, () -> new ProductService(dao).saveOrUpdate(p));
            assertTrue(e.getMessage().contains("estoque não pode ser negativo"));
            assertEquals(0, dao.chamadas());
            assertEquals(-1, p.getEstoque());
        }
    }

    @Test
    void custoNegativoOuForaDaColunaRecusadoAntesDoDao() {
        for (int id : new int[]{0, 1}) {
            for (String valor : new String[]{"-0.01", "100000000.00"}) {
                Produtos dao = new Produtos();
                Product p = produto(id, 0);
                p.setPrecoCusto(new BigDecimal(valor));
                DbException e = assertThrows(DbException.class, () -> new ProductService(dao).saveOrUpdate(p));
                assertTrue(e.getMessage().contains("custo deve estar entre"));
                assertEquals(0, dao.chamadas());
            }
        }
    }

    @Test
    void estoqueZeroECustoOpcionalZeroOuMaximoSaoValidos() throws Exception {
        for (BigDecimal custo : new BigDecimal[]{null, Money.ZERO, new BigDecimal("99999999.99")}) {
            Produtos dao = new Produtos();
            Product p = produto(0, 0);
            p.setPrecoCusto(custo);
            new ProductService(dao).saveOrUpdate(p);
            assertEquals(1, dao.consultas);
            assertEquals(1, dao.insercoes);
            assertEquals(0, dao.atualizacoes);
            assertEquals(0, dao.salvo.getEstoque());
            assertEquals(custo, dao.salvo.getPrecoCusto());
        }
    }

    @Test
    void atualizacaoValidaComEstoqueZeroContinuaFuncionando() throws Exception {
        Produtos dao = new Produtos();
        Product p = produto(1, 0);
        p.setPrecoCusto(new BigDecimal("5.25"));
        new ProductService(dao).saveOrUpdate(p);
        assertEquals(1, dao.consultas);
        assertEquals(0, dao.insercoes);
        assertEquals(1, dao.atualizacoes);
        assertEquals(new BigDecimal("5.25"), dao.salvo.getPrecoCusto());
    }

    @Test
    void falhaAoDesativarPreservaObjetoERetryRealizaNovaTentativa() {
        Produtos dao = new Produtos();
        Product carregado = produto(1, 10);
        dao.antesDeDesativar = () -> assertTrue(carregado.isAtivo(), "Só pode mudar a memória após o retorno do DAO.");
        dao.falharAntes = true;
        ProductService service = new ProductService(dao);
        assertThrows(DbException.class, () -> service.desativar(carregado));
        assertTrue(carregado.isAtivo());
        assertTrue(dao.registro.isAtivo());
        assertEquals(1, dao.desativacoes);
        service.desativar(carregado);
        assertFalse(carregado.isAtivo());
        assertFalse(dao.registro.isAtivo());
        assertEquals(2, dao.desativacoes);
        assertEquals(1, dao.ultimoIdDesativado);
        assertEquals(0, dao.atualizacoes);
        assertEquals(10, carregado.getEstoque());
    }

    @Test
    void desativacaoPreservaDadosAtuaisENaoExigeValidarDadosLegadosDoObjeto() {
        Produtos dao = new Produtos();
        dao.registro.setNomeProduto("Nome novo");
        dao.registro.setPrecoProduto(Money.parse("12"));
        dao.registro.setPrecoCusto(Money.parse("6"));
        Product legado = produto(1, -5);
        legado.setPrecoCusto(Money.parse("-1"));
        new ProductService(dao).desativar(legado);
        assertFalse(legado.isAtivo());
        assertEquals(9, dao.registro.getEstoque());
        assertEquals("Nome novo", dao.registro.getNomeProduto());
        assertEquals(Money.parse("12"), dao.registro.getPrecoProduto());
        assertEquals(Money.parse("6"), dao.registro.getPrecoCusto());
        assertEquals(1, dao.desativacoes);
        assertEquals(0, dao.consultas + dao.insercoes + dao.atualizacoes);
    }

    @Test
    void respostaIncertaNaoAlteraObjetoERetryIdempotenteConfirmaDesativacao() {
        Produtos dao = new Produtos();
        Product p = produto(1, 10);
        dao.falharDepois = true;
        ProductService service = new ProductService(dao);
        assertThrows(ResultadoTransacaoIncertoException.class, () -> service.desativar(p));
        assertTrue(p.isAtivo());
        assertFalse(dao.registro.isAtivo());
        service.desativar(p);
        assertFalse(p.isAtivo());
        assertEquals(2, dao.desativacoes);
        assertEquals(0, dao.atualizacoes);
    }

    @Test
    void produtoOuIdInvalidoRecusadosSemDao() {
        Produtos dao = new Produtos();
        ProductService service = new ProductService(dao);
        assertThrows(DbException.class, () -> service.saveOrUpdate(null));
        assertThrows(DbException.class, () -> service.desativar(null));
        assertThrows(DbException.class, () -> service.desativar(produto(0, 1)));
        assertEquals(0, dao.chamadas());
    }
}
