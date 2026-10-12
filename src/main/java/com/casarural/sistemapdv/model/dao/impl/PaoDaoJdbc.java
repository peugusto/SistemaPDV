package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.PaoDao;
import com.casarural.sistemapdv.model.entities.DiaPao;
import com.casarural.sistemapdv.model.entities.enums.TurnoPao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PaoDaoJdbc implements PaoDao {

    @Override
    public List<DiaPao> dias(int idProduto, LocalDate de, LocalDate ate) {
        Map<LocalDate, DiaPao> dias = new LinkedHashMap<>();
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            dias.put(d, new DiaPao(d));
        }

        // Vendas por dia e turno. Faixa de datas (sem DATE() no WHERE) para usar o índice de data_pedido (V9).
        String sqlVendas =
                "SELECT DATE(p.data_pedido) AS dia, " +
                "       CASE WHEN TIME(p.data_pedido) < ? THEN 'MANHA' ELSE 'TARDE' END AS turno, " +
                "       SUM(ip.quantidade) AS vendidos, MAX(TIME(p.data_pedido)) AS ultima " +
                "FROM itempedido ip INNER JOIN pedido p ON p.id_pedido = ip.id_pedido " +
                "WHERE ip.id_produto = ? AND p.data_pedido >= ? AND p.data_pedido < ? AND p.status NOT IN ('EM_ANDAMENTO', 'CANCELADO') " +
                "GROUP BY dia, turno";
        String sqlCompras =
                "SELECT data_compra, turno, quantidade FROM compra_pao " +
                "WHERE id_produto = ? AND data_compra BETWEEN ? AND ?";

        try (Connection conn = DB.getConnection()) {
            try (PreparedStatement st = conn.prepareStatement(sqlVendas)) {
                st.setTime(1, Time.valueOf(TurnoPao.TARDE.getInicio()));
                st.setInt(2, idProduto);
                st.setTimestamp(3, Timestamp.valueOf(de.atStartOfDay()));
                st.setTimestamp(4, Timestamp.valueOf(ate.plusDays(1).atStartOfDay()));
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        DiaPao dia = dias.get(rs.getDate("dia").toLocalDate());
                        if (dia == null) continue;
                        Time ultima = rs.getTime("ultima");
                        dia.setVendas(TurnoPao.valueOf(rs.getString("turno")), rs.getInt("vendidos"),
                                ultima == null ? null : ultima.toLocalTime());
                    }
                }
            }
            try (PreparedStatement st = conn.prepareStatement(sqlCompras)) {
                st.setInt(1, idProduto);
                st.setDate(2, Date.valueOf(de));
                st.setDate(3, Date.valueOf(ate));
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        DiaPao dia = dias.get(rs.getDate("data_compra").toLocalDate());
                        if (dia != null) {
                            dia.setComprado(TurnoPao.valueOf(rs.getString("turno")), rs.getInt("quantidade"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return new ArrayList<>(dias.values());
    }

    @Override
    public void registrarCompra(int idProduto, LocalDate data, TurnoPao turno, int quantidade) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO compra_pao (id_produto, data_compra, turno, quantidade) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE quantidade = ?")) {
            st.setInt(1, idProduto);
            st.setDate(2, Date.valueOf(data));
            st.setString(3, turno.name());
            st.setInt(4, quantidade);
            st.setInt(5, quantidade);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }
}
