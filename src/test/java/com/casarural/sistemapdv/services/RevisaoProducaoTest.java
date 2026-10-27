package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.RegraListaCompras;
import com.casarural.sistemapdv.model.entities.VendaProduto;
import com.casarural.sistemapdv.util.InstanciaUnica;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Correções da revisão de produção (16/10/2026). */
class RevisaoProducaoTest {

    /** Produtos em memória, sem banco. */
    static class ProdutosFalsos implements ProductDao {
        final Map<Integer, Product> porId = new HashMap<>();
        final Map<Integer, Integer> usos = new HashMap<>();
        int proximo = 1;
        boolean excluiu;

        Product novo(String codigo, String nome) {
            Product p = new Product(codigo, nome, Money.parse("1"), 0);
            p.setIdProduto(proximo++);
            porId.put(p.getIdProduto(), p);
            return p;
        }
        public Optional<Product> findByCodBarras(String c) {
            return porId.values().stream().filter(p -> p.getCodBarras().equals(c)).findFirst();
        }
        public int contarUsos(int id) { return usos.getOrDefault(id, 0); }
        public void insert(Product obj) { obj.setIdProduto(proximo++); porId.put(obj.getIdProduto(), obj); }
        public Optional<Product> findById(Integer id) { return Optional.ofNullable(porId.get(id)); }
        public List<Product> findAll() { return new ArrayList<>(porId.values()); }
        public void update(Product obj) { porId.put(obj.getIdProduto(), obj); }
        public void deleteById(Integer id) { porId.remove(id); excluiu = true; }
        public Product instantiate(ResultSet rs) { return null; }
    }

    static class ConfigFalsa implements ConfiguracaoDao {
        final Map<String, String> v = new HashMap<>();
        public Optional<String> get(String c) { return Optional.ofNullable(v.get(c)); }
        public void set(String c, String valor) { v.put(c, valor); }
    }

    // ---------- Item 11: espaços nas pontas ----------

    @Test
    void codigoENomeSaoGravadosSemEspacos() throws Exception {
        ProdutosFalsos dao = new ProdutosFalsos();
        ProductService s = new ProductService(dao);
        Product p = new Product("  7891234 ", "  Arroz 5kg ", Money.parse("25"), 1);
        s.saveOrUpdate(p);
        assertEquals("7891234", p.getCodBarras());
        assertEquals("Arroz 5kg", p.getNomeProduto());
        assertTrue(s.findByCodBarras(" 7891234  ").isPresent());   // busca com espaço acha
        assertTrue(s.findByCodBarras("   ").isEmpty());
    }

    // ---------- Item 10: tamanhos do banco ----------

    @Test
    void nomeMaiorQueOBancoTemMensagemClara() {
        ProductService s = new ProductService(new ProdutosFalsos());
        Product p = new Product("1", "x".repeat(Product.TAMANHO_MAX_NOME + 1), Money.parse("1"), 0);
        DbException e = assertThrows(DbException.class, () -> s.saveOrUpdate(p));
        assertTrue(e.getMessage().contains("no máximo " + Product.TAMANHO_MAX_NOME));
        Product semNome = new Product("1", "   ", Money.parse("1"), 0);
        assertThrows(DbException.class, () -> s.saveOrUpdate(semNome));
    }

    // ---------- Item 8: excluir x desativar ----------

    @Test
    void produtoVendidoNaoExcluiDesativa() {
        ProdutosFalsos dao = new ProdutosFalsos();
        Product vendido = dao.novo("10", "Cigarro");
        dao.usos.put(vendido.getIdProduto(), 3);
        ProductService s = new ProductService(dao);

        assertTrue(s.temHistorico(vendido.getIdProduto()));
        assertThrows(DbException.class, () -> s.delete(vendido.getIdProduto()));
        assertFalse(dao.excluiu);

        s.desativar(vendido);
        assertFalse(dao.porId.get(vendido.getIdProduto()).isAtivo());
    }

    @Test
    void produtoNuncaVendidoPodeSerExcluido() {
        ProdutosFalsos dao = new ProdutosFalsos();
        Product novo = dao.novo("11", "Teste");
        new ProductService(dao).delete(novo.getIdProduto());
        assertTrue(dao.excluiu);
    }

    @Test
    void desativadoNaoEntraNaListaDeCompras() {
        Product p = new Product("1", "Coca", Money.parse("5"), 0);
        p.setAtivo(false);
        assertTrue(RegraListaCompras.montar(List.of(new VendaProduto(p, 10, 10, Money.ZERO, null))).isEmpty());
    }

    // ---------- Item 9: pão recadastrado ----------

    @Test
    void paoRecadastradoEAchadoPeloCodigoEAConfiguracaoAtualiza() {
        ProdutosFalsos produtos = new ProdutosFalsos();
        ConfigFalsa config = new ConfigFalsa();
        config.set(PaoService.CHAVE_PRODUTO, "999");                // id antigo, excluído
        Product novoPao = produtos.novo(PaoService.CODIGO_PAO, "PÃO FRANCÊS");
        PaoService s = new PaoService(null, config, produtos);

        assertEquals(novoPao.getIdProduto(), s.produtoPao().orElseThrow().getIdProduto());
        assertEquals(String.valueOf(novoPao.getIdProduto()), config.v.get(PaoService.CHAVE_PRODUTO));
    }

    @Test
    void semPaoNenhumFicaVazio() {
        assertTrue(new PaoService(null, new ConfigFalsa(), new ProdutosFalsos()).produtoPao().isEmpty());
    }

    // ---------- Item 12: nome do cliente ----------

    @Test
    void clienteSemNomeERecusado() {
        Customer c = new Customer();
        c.setNomeCliente("   ");
        assertThrows(DbException.class, () -> CustomerService.validarNome(c));
        c.setNomeCliente("  Vera  ");
        CustomerService.validarNome(c);
        assertEquals("Vera", c.getNomeCliente());
    }

    // ---------- Item 2: configuração fora do programa ----------

    @Test
    void configuracaoInformadaNaAberturaTemPrioridade() throws Exception {
        Path tmp = Files.createTempFile("db", ".properties");
        String antes = System.getProperty("pdv.config");
        try {
            System.setProperty("pdv.config", tmp.toString());
            assertEquals(tmp, DB.arquivoConfiguracao().orElseThrow());
            System.setProperty("pdv.config", tmp + ".naoexiste");
            assertTrue(DB.arquivoConfiguracao().isEmpty());
        } finally {
            if (antes == null) System.clearProperty("pdv.config"); else System.setProperty("pdv.config", antes);
            Files.deleteIfExists(tmp);
        }
    }

    /** Instalador testado no PC de desenvolvimento: a IDE continua no banco de desenvolvimento. */
    @Test
    void configuracaoDoProjetoVemAntesDaDoInstalador() {
        Assumptions.assumeTrue(System.getProperty("pdv.config") == null);
        Assumptions.assumeTrue(Files.isRegularFile(DB.devConfigPath()), "só no PC de desenvolvimento");
        assertEquals(DB.devConfigPath(), DB.arquivoConfiguracao().orElseThrow());
    }

    @Test
    void configuracaoNaoVemMaisDeDentroDoPrograma() {
        assertNull(RevisaoProducaoTest.class.getClassLoader().getResource("db.properties"),
                "db.properties não pode estar no classpath (iria dentro do .jar com as senhas)");
    }

    // ---------- Item 14: abrir duas vezes ----------

    @Test
    void segundaAberturaEBloqueada() {
        assertTrue(InstanciaUnica.obter());
        assertFalse(InstanciaUnica.obter());
    }
}
