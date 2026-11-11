package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.CustomerDao;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.RegraFiado;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDaoJdbc implements CustomerDao {
    private final java.util.function.Supplier<Connection> conexoes;
    public CustomerDaoJdbc() { this(DB::getConnection); }
    public CustomerDaoJdbc(java.util.function.Supplier<Connection> conexoes) { this.conexoes=java.util.Objects.requireNonNull(conexoes); }

    @Override
    public void insert(Customer obj) {
        int idCliente = TransacaoJdbc.executar(conexoes, "cadastrar o cliente", conn -> {
            try (PreparedStatement st = conn.prepareStatement(
                    "INSERT INTO cliente (nome_cliente, limite_credito, situacao_fiado, telefone) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1, obj.getNomeCliente());
                st.setBigDecimal(2, obj.getLimiteCredito());
                st.setString(3, obj.getSituacaoFiado().toString());
                st.setString(4, obj.getTelefone());

                if (st.executeUpdate() != 1) {
                    throw new DbException("Não foi possível inserir o cliente.");
                }
                try (ResultSet rs = st.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new DbException("Não foi possível identificar o novo cliente.");
                    }
                    int id = rs.getInt(1);
                    if (id <= 0) {
                        throw new DbException("O banco retornou um identificador de cliente inválido.");
                    }
                    return id;
                }
            }
        });
        obj.setIdCliente(idCliente);
    }

    @Override
    public Optional<Customer> findById(Integer id) {
        try (Connection conn = conexoes.get();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM cliente WHERE id_cliente = ?")) {
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
    public List<Customer> findAll() {
        try (Connection conn = conexoes.get();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT c.*, " +
                             "COALESCE((SELECT SUM(p.valor_total) FROM pedido p WHERE p.id_cliente = c.id_cliente AND p.status = 'FIADO'), 0) " +
                             "- " +
                             "COALESCE((SELECT SUM(pg.valor_pago) FROM pagamento pg INNER JOIN pedido p ON pg.id_pedido = p.id_pedido WHERE p.id_cliente = c.id_cliente AND p.status = 'FIADO' AND pg.metodo_pagamento <> 'FIADO'), 0) " +
                             "AS total_devendo, " +
                             // Valor em atraso: o que falta pagar das compras fiadas feitas antes do limite
                             // do prazo (RegraFiado). É a mesma conta de Order.getFalta(), por pedido.
                             "COALESCE((SELECT SUM(p.valor_total - COALESCE((SELECT SUM(pg.valor_pago) FROM pagamento pg " +
                             "    WHERE pg.id_pedido = p.id_pedido AND pg.metodo_pagamento <> 'FIADO'), 0)) " +
                             "  FROM pedido p WHERE p.id_cliente = c.id_cliente AND p.status = 'FIADO' AND p.data_pedido < ?), 0) " +
                             "AS valor_em_atraso " +
                             "FROM cliente c ORDER BY nome_cliente")) {
            st.setTimestamp(1, Timestamp.valueOf(RegraFiado.limite(LocalDateTime.now())));

            try (ResultSet rs = st.executeQuery()) {
                List<Customer> list = new ArrayList<>();
                while (rs.next()) {
                    Customer obj = instantiate(rs);
                    obj.setTotalDevendo(rs.getBigDecimal("total_devendo"));
                    obj.setValorEmAtraso(rs.getBigDecimal("valor_em_atraso"));
                    list.add(obj);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void update(Customer obj) {
        Object[] original = obj.getEstadoOriginal();
        BigDecimal dividaAtual = TransacaoJdbc.executar(conexoes, "atualizar o cliente", conn -> {
            // Mesmo bloqueio de cliente que uma venda ou um recebimento no fiado usa.
            try (PreparedStatement st = conn.prepareStatement("SELECT id_cliente FROM cliente WHERE id_cliente = ? "
                    + "AND limite_credito <=> ? AND situacao_fiado <=> ? FOR UPDATE")) {
                st.setInt(1, obj.getIdCliente());
                st.setObject(2, original[0]);
                st.setString(3, original[1] == null ? null : original[1].toString());
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) throw new DbException(
                            "O limite ou o bloqueio deste cliente mudou. Reabra a edição para conferir os dados antes de salvar.");
                }
            }
            BigDecimal divida = OrderDaoJdbc.carregarFiadosEmAberto(conn, obj.getIdCliente()).stream()
                    .map(Order::getFalta).reduce(Money.ZERO, BigDecimal::add);
            if (obj.getLimiteCredito().compareTo(divida) < 0) {
                throw new DbException("O limite de crédito (" + Money.format(obj.getLimiteCredito())
                        + ") não pode ser menor que a dívida atual (" + Money.format(divida) + ").");
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "UPDATE cliente SET nome_cliente = ?, limite_credito = ?, situacao_fiado = ?, telefone = ? WHERE id_cliente = ?")) {
                st.setString(1, obj.getNomeCliente());
                st.setBigDecimal(2, obj.getLimiteCredito());
                st.setString(3, obj.getSituacaoFiado().toString());
                st.setString(4, obj.getTelefone());
                st.setInt(5, obj.getIdCliente());
                st.executeUpdate();
            }
            return divida;
        });
        obj.setTotalDevendo(dividaAtual);
        obj.registrarEstadoOriginal();
    }

    @Override
    public int countOrders(Integer idCliente) {
        try (Connection conn = conexoes.get();
             PreparedStatement st = conn.prepareStatement("SELECT COUNT(*) FROM pedido WHERE id_cliente = ?")) {
            st.setInt(1, idCliente);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(Integer id) {
        try (Connection conn = conexoes.get();
             PreparedStatement st = conn.prepareStatement("DELETE FROM cliente WHERE id_cliente = ?")) {
            st.setInt(1, id);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    public Customer instantiate(ResultSet rs) throws SQLException {
        Customer obj = new Customer();
        obj.setIdCliente(rs.getInt("id_cliente"));
        obj.setNomeCliente(rs.getString("nome_cliente"));
        obj.setLimiteCredito(rs.getBigDecimal("limite_credito"));
        obj.setSituacaoFiado(CustomerStatus.valueOf(rs.getString("situacao_fiado")));
        obj.setTelefone(rs.getString("telefone"));
        obj.registrarEstadoOriginal();
        return obj;
    }
}
