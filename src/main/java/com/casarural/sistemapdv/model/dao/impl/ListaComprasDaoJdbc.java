package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ListaComprasDao;
import com.casarural.sistemapdv.model.entities.RegraListaCompras;
import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

public class ListaComprasDaoJdbc implements ListaComprasDao {
    private final Supplier<Connection> conexoes;
    public ListaComprasDaoJdbc() { this(DB::getConnection); }
    public ListaComprasDaoJdbc(Supplier<Connection> conexoes) { this.conexoes = Objects.requireNonNull(conexoes); }
    @Override public List<Item> buscarPendencias() {
        String sql = "SELECT * FROM produto WHERE ativo AND controla_estoque AND tipo_venda <> 'PESAVEL' "
                + "AND quantidade_repor > 0 ORDER BY (estoque <= 0) DESC, nome_produto, id_produto";
        try (Connection conn = conexoes.get(); PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            List<Item> itens = new ArrayList<>();
            ProductDaoJdbc produtos = new ProductDaoJdbc();
            while (rs.next()) {
                long quantidade = rs.getLong("quantidade_repor");
                if (quantidade > Integer.MAX_VALUE) throw new DbException(
                        "A pendência de reposição de um produto ultrapassa o limite da lista. Peça ao responsável para conferir os lançamentos.");
                itens.add(RegraListaCompras.acumulado(produtos.instantiate(rs), Math.toIntExact(quantidade)));
            }
            return itens;
        } catch (SQLException e) { throw new DbException("Não foi possível atualizar a lista de compras. Tente novamente.", e); }
    }
}
