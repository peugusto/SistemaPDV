-- A devolução é lançada no caixa atual; os pagamentos preservam sua origem no caixa encerrado.
CREATE TABLE devolucao_venda (
    id_pedido INT NOT NULL PRIMARY KEY,
    id_caixa INT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    status_original VARCHAR(20) NOT NULL,
    valor_total DECIMAL(10,2) NOT NULL,
    fiado_baixado DECIMAL(10,2) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    registrada_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_devolucao_caixa (id_caixa, registrada_em),
    KEY idx_devolucao_data (registrada_em),
    FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido),
    FOREIGN KEY (id_caixa) REFERENCES caixa(id_caixa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE devolucao_venda_forma (
    id_pedido INT NOT NULL,
    metodo VARCHAR(10) NOT NULL,
    valor_compensado DECIMAL(10,2) NOT NULL,
    valor_devolvido DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id_pedido, metodo),
    FOREIGN KEY (id_pedido) REFERENCES devolucao_venda(id_pedido)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Só os tipos novos mantêm a entrada histórica. Estornos antigos não são reativados.
CREATE VIEW pagamento_caixa AS
SELECT id_pagamento, id_pedido, valor_pago, data_pagamento, metodo_pagamento, origem, id_caixa, id_recebimento
FROM pagamento
UNION ALL
SELECT pe.id_pagamento, pe.id_pedido, pe.valor_pago, pe.data_pagamento, pe.metodo_pagamento, pe.origem, pe.id_caixa, pe.id_recebimento
FROM pagamento_estornado pe JOIN devolucao_venda d ON d.id_pedido=pe.id_pedido
WHERE pe.tipo IN ('DEVOLUCAO_EXTERNA','TROCA_EXTERNA');

-- Os relatórios conservam a venda na data original e descontam a devolução na data da operação.
CREATE VIEW movimento_venda_relatorio AS
SELECT p.id_pedido,p.data_pedido,p.valor_total,p.desconto,1 AS sinal
FROM pedido p LEFT JOIN devolucao_venda d ON d.id_pedido=p.id_pedido
WHERE p.status NOT IN ('EM_ANDAMENTO','CANCELADO') OR d.id_pedido IS NOT NULL
UNION ALL
SELECT -p.id_pedido,d.registrada_em,-p.valor_total,-p.desconto,-1
FROM devolucao_venda d JOIN pedido p ON p.id_pedido=d.id_pedido;
