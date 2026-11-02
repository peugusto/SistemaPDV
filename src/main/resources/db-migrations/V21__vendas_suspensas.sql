-- Rascunhos não são pedidos: não alteram caixa, pagamentos, fiado, estoque ou reposição.
CREATE TABLE venda_suspensa (
    id CHAR(36) NOT NULL PRIMARY KEY,
    identificacao VARCHAR(80) NOT NULL DEFAULT '',
    criada_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    versao BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    sessao CHAR(36) NULL,
    token CHAR(36) NULL,
    ultima_operacao CHAR(36) NOT NULL,
    assinatura CHAR(64) NOT NULL,
    id_pedido INT NULL,
    UNIQUE KEY uk_suspensa_pedido (id_pedido),
    KEY idx_suspensa_estado_data (estado, atualizada_em),
    CONSTRAINT fk_suspensa_pedido FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE venda_suspensa_item (
    venda_id CHAR(36) NOT NULL,
    linha INT NOT NULL,
    id_produto INT NOT NULL,
    nome_produto VARCHAR(30) NOT NULL,
    cod_barras VARCHAR(60) NOT NULL,
    quantidade INT NOT NULL,
    preco_unitario DECIMAL(10,2) NOT NULL,
    tipo_venda VARCHAR(20) NOT NULL,
    preco_cadastro DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (venda_id, linha),
    CONSTRAINT fk_suspensa_item FOREIGN KEY (venda_id) REFERENCES venda_suspensa(id) ON DELETE CASCADE
    -- Sem FK para produto: o rascunho conserva o nome e pode avisar se o cadastro foi excluído.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
