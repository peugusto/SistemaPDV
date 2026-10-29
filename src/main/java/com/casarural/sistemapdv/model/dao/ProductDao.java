package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.EntradaEstoque;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public interface ProductDao extends GenericDao<Product> {
    Optional<Product> findByCodBarras(String codBarras);

    /** Incremento atômico, sem sobrescrever metadados; repetir a chave não duplica a entrada. */
    void registrarEntrada(EntradaEstoque entrada);

    /** Em quantas vendas e compras de pão o produto aparece (se > 0, não pode ser excluído). */
    int contarUsos(int idProduto);
}
