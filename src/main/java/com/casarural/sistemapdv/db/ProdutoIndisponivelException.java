package com.casarural.sistemapdv.db;

import java.util.Set;

public class ProdutoIndisponivelException extends DbException {
    private final Set<Integer> ids;

    public ProdutoIndisponivelException(Set<Integer> ids) {
        super("Há produtos indisponíveis no carrinho. Confira os itens antes de finalizar a venda.");
        this.ids = Set.copyOf(ids);
    }

    public Set<Integer> getIds() {
        return ids;
    }
}
