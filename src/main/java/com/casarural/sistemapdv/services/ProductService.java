package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ProductService {
    private ProductDao dao;

    public ProductService(ProductDao dao) {
        this.dao = dao;
    }

    /** Já foi vendido (ou registrado no Pão do dia): não pode ser excluído, só desativado. */
    public boolean temHistorico(int idProduto) {
        return dao.contarUsos(idProduto) > 0;
    }

    /** Exclui de vez. Só para produto sem histórico; com histórico, use desativar. */
    public void delete(Integer id) {
        if (temHistorico(id)) {
            throw new DbException("Este produto já foi vendido e não pode ser excluído. Desative-o em vez disso.");
        }
        dao.deleteById(id);
    }

    /** Some da venda, da busca e da lista de compras; o histórico de vendas continua. */
    public void desativar(Product p) {
        p.setAtivo(false);
        dao.update(p);
    }

    /** Busca pelo código, sem os espaços das pontas (um espaço a mais não faz o produto "sumir"). */
    public Optional<Product> findByCodBarras(String codBarras) {
        if (codBarras == null || codBarras.isBlank()) return Optional.empty();
        return dao.findByCodBarras(codBarras.trim());
    }

    public void saveOrUpdate(Product obj) throws SQLException {
        normalizar(obj);
        Optional<Product> mesmoCodigo = dao.findByCodBarras(obj.getCodBarras());
        if (mesmoCodigo.isPresent() && mesmoCodigo.get().getIdProduto() != obj.getIdProduto()) {
            throw new DbException(String.format("O código de barras '%s' já está cadastrado no produto '%s'.",
                    obj.getCodBarras(), mesmoCodigo.get().getNomeProduto()));
        }

        if (obj.getIdProduto() > 0) {
            dao.update(obj);
        } else {
            dao.insert(obj);
        }
    }

    /**
     * Tira os espaços das pontas e confere os tamanhos antes de gravar, com mensagem clara
     * (em vez do erro técnico "Data too long" do banco).
     */
    static void normalizar(Product p) {
        String codigo = p.getCodBarras() == null ? "" : p.getCodBarras().trim();
        String nome = p.getNomeProduto() == null ? "" : p.getNomeProduto().trim();
        if (codigo.isEmpty()) throw new DbException("Digite o código do produto.");
        if (nome.isEmpty()) throw new DbException("Digite o nome do produto.");
        if (codigo.length() > Product.TAMANHO_MAX_CODIGO) {
            throw new DbException("O código pode ter no máximo " + Product.TAMANHO_MAX_CODIGO + " caracteres.");
        }
        if (nome.length() > Product.TAMANHO_MAX_NOME) {
            throw new DbException("O nome pode ter no máximo " + Product.TAMANHO_MAX_NOME + " letras ("
                    + nome.length() + " digitadas).");
        }
        p.setCodBarras(codigo);
        p.setNomeProduto(nome);
    }

    public List<Product> findAll() {
        return dao.findAll();
    }
}
