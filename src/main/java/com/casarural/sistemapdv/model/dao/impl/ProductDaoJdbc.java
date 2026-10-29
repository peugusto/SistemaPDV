package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.db.EntradaEstoqueNaoAplicadaException;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.EntradaEstoque;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductDaoJdbc implements ProductDao {

    private static final Logger log = LoggerFactory.getLogger(ProductDaoJdbc.class);
    private final Supplier<Connection> conexoes;

    public ProductDaoJdbc() { this(DB::getConnection); }

    /** Permite verificar transações com conexões isoladas, sem usar a configuração da loja. */
    public ProductDaoJdbc(Supplier<Connection> conexoes) {
        this.conexoes = Objects.requireNonNull(conexoes);
    }

    @Override
    public void desativarPorId(int idProduto) {
        if (idProduto <= 0) throw new DbException("Selecione um produto válido para desativar.");
        boolean confirmado = false;
        try (Connection conn = conexoes.get()) {
            conn.setAutoCommit(false);
            boolean commitIniciado = false;
            try {
                int alterados;
                try (PreparedStatement st = conn.prepareStatement("UPDATE produto SET ativo = FALSE WHERE id_produto = ?")) {
                    st.setInt(1, idProduto);
                    alterados = st.executeUpdate();
                }
                // Alguns drivers contam apenas linhas modificadas: um produto já inativo pode retornar zero.
                if (alterados == 0) {
                    try (PreparedStatement st = conn.prepareStatement(
                            "SELECT id_produto FROM produto WHERE id_produto = ? FOR UPDATE")) {
                        st.setInt(1, idProduto);
                        try (ResultSet rs = st.executeQuery()) {
                            if (!rs.next()) {
                                throw new DbException("Produto não encontrado. Atualize a lista e tente novamente.");
                            }
                        }
                    }
                } else if (alterados != 1) {
                    throw new DbException("Não foi possível confirmar a desativação do produto.");
                }
                commitIniciado = true;
                conn.commit();
                confirmado = true;
            } catch (SQLException | RuntimeException e) {
                boolean rollbackConfirmado = false;
                try {
                    conn.rollback();
                    rollbackConfirmado = true;
                } catch (SQLException | RuntimeException rollback) {
                    e.addSuppressed(rollback);
                    try {
                        conn.abort(Runnable::run);
                    } catch (SQLException | RuntimeException abort) {
                        e.addSuppressed(abort);
                    }
                }
                if (commitIniciado || !rollbackConfirmado) {
                    throw new ResultadoTransacaoIncertoException("desativação do produto", e);
                }
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new DbException("Não foi possível desativar o produto. Tente novamente.", e);
            }
        } catch (SQLException | RuntimeException e) {
            if (confirmado) {
                log.warn("Produto {} desativado, mas houve falha ao liberar a conexão.", idProduto, e);
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new DbException("Não foi possível iniciar a desativação do produto.", e);
            }
        }
    }

    @Override
    public void registrarEntrada(EntradaEstoque entrada) {
        Objects.requireNonNull(entrada, "Entrada de estoque não informada.");
        boolean confirmado = false;
        try (Connection conn = conexoes.get()) {
            conn.setAutoCommit(false);
            boolean commitIniciado = false;
            boolean nova = false;
            try {
                nova = inserirOperacao(conn, entrada);
                if (nova) {
                    try (PreparedStatement st = conn.prepareStatement(
                            "UPDATE produto SET estoque = estoque + ?, "
                                    + "preco_custo = COALESCE(?, preco_custo) "
                                    + "WHERE id_produto = ? AND ativo AND controla_estoque "
                                    + "AND estoque >= 0 AND estoque <= ?")) {
                        st.setInt(1, entrada.getQuantidade());
                        st.setBigDecimal(2, entrada.getCustoInformado());
                        st.setInt(3, entrada.getIdProduto());
                        st.setInt(4, Integer.MAX_VALUE - entrada.getQuantidade());
                        if (st.executeUpdate() != 1) {
                            throw new DbException("Não foi possível dar entrada: confira se o produto está ativo, "
                                    + "controla estoque e se a quantidade cabe no saldo permitido. Pesquise novamente.");
                        }
                    }
                }
                commitIniciado = true;
                conn.commit();
                confirmado = true;
            } catch (SQLException | RuntimeException e) {
                boolean rollbackConfirmado = false;
                try {
                    conn.rollback();
                    rollbackConfirmado = true;
                } catch (SQLException | RuntimeException rollback) {
                    e.addSuppressed(rollback);
                    // Uma conexão cuja transação não pôde ser desfeita não volta saudável ao pool.
                    try {
                        conn.abort(Runnable::run);
                    } catch (SQLException | RuntimeException abort) {
                        e.addSuppressed(abort);
                    }
                }
                if (commitIniciado || !rollbackConfirmado) {
                    throw new ResultadoTransacaoIncertoException("entrada de estoque", e);
                }
                if (nova) {
                    throw new EntradaEstoqueNaoAplicadaException(
                            e instanceof DbException ? e.getMessage() : "A entrada não foi aplicada. Tente novamente.", e);
                }
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new DbException("Não foi possível registrar a entrada de estoque.", e);
            }
        } catch (SQLException | RuntimeException e) {
            // Falha ao devolver a conexão não desfaz um commit que já foi confirmado.
            if (confirmado) {
                log.warn("Entrada de estoque {} confirmada, mas houve falha ao liberar a conexão.", entrada.getIdOperacao(), e);
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new DbException("Não foi possível iniciar a entrada de estoque.", e);
            }
        }
    }

    private boolean inserirOperacao(Connection conn, EntradaEstoque entrada) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
                "INSERT INTO entrada_estoque (id_operacao, id_produto, quantidade, custo_informado) VALUES (?, ?, ?, ?)")) {
            st.setString(1, entrada.getIdOperacao().toString());
            st.setInt(2, entrada.getIdProduto());
            st.setInt(3, entrada.getQuantidade());
            st.setBigDecimal(4, entrada.getCustoInformado());
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() != 1062) throw e;
        }
        // A chave única também serializa tentativas simultâneas da mesma operação.
        try (PreparedStatement st = conn.prepareStatement(
                "SELECT id_produto, quantidade, custo_informado FROM entrada_estoque WHERE id_operacao = ? FOR UPDATE")) {
            st.setString(1, entrada.getIdOperacao().toString());
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next() || !entrada.mesmosDados(rs.getInt("id_produto"), rs.getInt("quantidade"),
                        rs.getBigDecimal("custo_informado"))) {
                    throw new DbException("A identificação desta entrada já foi usada com outros dados. "
                            + "A entrada não foi alterada; confira a operação antes de tentar novamente.");
                }
            }
        }
        return false;
    }

    @Override
    public void insert(Product obj) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO produto (cod_barras, nome_produto, preco_produto, estoque, permite_fiado, tipo_venda, controla_estoque, preco_custo, ativo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {

            st.setString(1, obj.getCodBarras());
            st.setString(2, obj.getNomeProduto());
            st.setBigDecimal(3, obj.getPrecoProduto());
            st.setInt(4, obj.getEstoque());
            st.setBoolean(5, obj.isPermiteFiado());
            st.setString(6, obj.getTipoVenda().name());
            st.setBoolean(7, obj.isControlaEstoque());
            st.setBigDecimal(8, obj.getPrecoCusto());
            st.setBoolean(9, obj.isAtivo());

            int rowsAffected = st.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet rs = st.getGeneratedKeys()) {
                    if (rs.next()) {
                        obj.setIdProduto(rs.getInt(1));
                    }
                }
            } else {
                throw new DbException("Erro inesperado! Nenhuma linha alterada.");
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Product> findById(Integer id) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM produto WHERE id_produto = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(instantiate(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public List<Product> findAll() {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM produto ORDER BY nome_produto");
             ResultSet rs = st.executeQuery()) {

            List<Product> list = new ArrayList<>();
            while (rs.next()) {
                list.add(instantiate(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void update(Product obj) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE produto SET cod_barras = ?, nome_produto = ?, preco_produto = ?, estoque = ?, permite_fiado = ?, "
                             + "tipo_venda = ?, controla_estoque = ?, preco_custo = ?, ativo = ? WHERE id_produto = ?")) {

            st.setString(1, obj.getCodBarras());
            st.setString(2, obj.getNomeProduto());
            st.setBigDecimal(3, obj.getPrecoProduto());
            st.setInt(4, obj.getEstoque());
            st.setBoolean(5, obj.isPermiteFiado());
            st.setString(6, obj.getTipoVenda().name());
            st.setBoolean(7, obj.isControlaEstoque());
            st.setBigDecimal(8, obj.getPrecoCusto());
            st.setBoolean(9, obj.isAtivo());
            st.setInt(10, obj.getIdProduto());

            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(Integer id) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("DELETE FROM produto WHERE id_produto = ?")) {
            st.setInt(1, id);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }


    /** Em quantas vendas e compras de pão o produto aparece (se > 0, não pode ser excluído). */
    @Override
    public int contarUsos(int idProduto) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT (SELECT COUNT(*) FROM itempedido WHERE id_produto = ?) " +
                     "     + (SELECT COUNT(*) FROM compra_pao WHERE id_produto = ?)")) {
            st.setInt(1, idProduto);
            st.setInt(2, idProduto);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    public Product instantiate(ResultSet rs) throws SQLException {
        Product prod = new Product();
        prod.setIdProduto(rs.getInt("id_produto"));
        prod.setCodBarras(rs.getString("cod_barras"));
        prod.setNomeProduto(rs.getString("nome_produto"));
        prod.setPrecoProduto(rs.getBigDecimal("preco_produto"));

        prod.setEstoque(rs.getInt("estoque"));
        prod.setPermiteFiado(rs.getBoolean("permite_fiado"));
        prod.setTipoVenda(TipoVenda.valueOf(rs.getString("tipo_venda")));
        prod.setControlaEstoque(rs.getBoolean("controla_estoque"));
        prod.setPrecoCusto(rs.getBigDecimal("preco_custo"));
        prod.setAtivo(rs.getBoolean("ativo"));
        return prod;
    }

    @Override
    public Optional<Product> findByCodBarras(String codBarras) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM produto WHERE cod_barras = ?")) {
            st.setString(1, codBarras);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(instantiate(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }
}
