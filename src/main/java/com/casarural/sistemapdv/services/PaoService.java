package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;
import com.casarural.sistemapdv.model.dao.PaoDao;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.DiaPao;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.TurnoPao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Relatório "Pão do dia": qual produto é o pão, histórico e registro da compra. */
public class PaoService {

    /** Chave em configuracao com o id do produto pão (criada pela V12). */
    public static final String CHAVE_PRODUTO = "pao.id_produto";
    /** Quantos dias de histórico a tela carrega: cobre as últimas 4 semanas do mesmo dia com folga. */
    public static final int DIAS_HISTORICO = 42;

    private final PaoDao dao;
    private final ConfiguracaoDao configuracao;
    private final ProductDao produtos;

    public PaoService(PaoDao dao, ConfiguracaoDao configuracao, ProductDao produtos) {
        this.dao = dao;
        this.configuracao = configuracao;
        this.produtos = produtos;
    }

    /** O produto pão; vazio se a configuração não existir ou o produto foi excluído. */
    public Optional<Product> produtoPao() {
        return configuracao.get(CHAVE_PRODUTO)
                .flatMap(id -> {
                    try {
                        return produtos.findById(Integer.parseInt(id.trim()));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    /** Do dia mais antigo até "hoje", um DiaPao por dia. */
    public List<DiaPao> historico(Product pao, LocalDate hoje) {
        return dao.dias(pao.getIdProduto(), hoje.minusDays(DIAS_HISTORICO), hoje);
    }

    public void registrarCompra(Product pao, LocalDate data, TurnoPao turno, int quantidade) {
        if (quantidade < 0) throw new IllegalArgumentException("Quantidade não pode ser negativa.");
        dao.registrarCompra(pao.getIdProduto(), data, turno, quantidade);
    }
}
