-- Entrada e chave da tentativa são gravadas na mesma transação.
-- Repetir após resposta perdida confirma a operação, sem somar novamente.
CREATE TABLE entrada_estoque (
    id_operacao     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    id_produto      INT NOT NULL,
    quantidade      INT NOT NULL,
    custo_informado DECIMAL(10,2) NULL,
    criado_em       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_operacao),
    -- ID histórico: sem FK para manter a chave mesmo se um produto sem vendas for excluído.
    -- Novas operações validam a existência do produto no UPDATE da mesma transação.
    KEY idx_entrada_estoque_produto (id_produto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
