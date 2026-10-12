-- V13: cancelamento de venda, troca, estorno de recebimento de fiado e desconto.

-- Desconto da venda (R$). valor_total continua sendo o que o cliente pagou: soma dos itens - desconto.
-- Cancelamento: a venda fica com status CANCELADO, com quando, por quê e (na troca) qual venda a substituiu.
ALTER TABLE pedido
    ADD COLUMN desconto            DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN cancelado_em        DATETIME     NULL,
    ADD COLUMN motivo_cancelamento VARCHAR(255) NULL,
    ADD COLUMN id_pedido_troca     INT          NULL;

-- Pagamentos desfeitos (venda cancelada, troca, recebimento de fiado estornado).
-- Saem da tabela pagamento e vêm para cá: o fechamento do caixa, a dívida do cliente e os relatórios,
-- que somam a tabela pagamento, ficam certos sozinhos, e o histórico do que foi desfeito fica guardado.
CREATE TABLE pagamento_estornado (
    id_pagamento     INT           NOT NULL,
    id_pedido        INT           NOT NULL,
    valor_pago       DECIMAL(10,2) NOT NULL,
    data_pagamento   DATETIME      NULL,
    metodo_pagamento VARCHAR(10)   NULL,
    origem           VARCHAR(20)   NOT NULL,
    id_caixa         INT           NULL,
    estornado_em     DATETIME      NOT NULL,
    tipo             VARCHAR(20)   NOT NULL,  -- CANCELAMENTO | TROCA | ESTORNO_RECEBIMENTO
    motivo           VARCHAR(255)  NULL,
    PRIMARY KEY (id_pagamento),
    KEY idx_estornado_pedido (id_pedido)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
