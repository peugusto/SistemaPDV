-- Perdas e correções manuais. Entradas e baixas de vendas mantêm seus registros próprios.
CREATE TABLE ajuste_estoque (
    id_ajuste BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_operacao CHAR(36) NOT NULL,
    id_produto INT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    saldo_esperado INT NOT NULL,
    valor_informado INT NOT NULL,
    saldo_antes INT NOT NULL,
    saldo_depois INT NOT NULL,
    reposicao_antes BIGINT NOT NULL,
    reposicao_depois BIGINT NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    nome_produto VARCHAR(30) NOT NULL,
    cod_barras VARCHAR(60) NOT NULL,
    registrado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_ajuste_operacao (id_operacao),
    KEY idx_ajuste_produto_data (id_produto, registrado_em),
    CONSTRAINT fk_ajuste_produto FOREIGN KEY (id_produto) REFERENCES produto(id_produto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
