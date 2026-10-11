-- V6: dinheiro que entra ou sai da gaveta sem ser venda.
--   SANGRIA -> retirada (ex.: pagar fornecedor, levar dinheiro para casa)
--   REFORCO -> entrada (ex.: trazer mais troco)

CREATE TABLE movimento_caixa (
    id_movimento   INT           NOT NULL AUTO_INCREMENT,
    id_caixa       INT           NOT NULL,
    tipo           VARCHAR(10)   NOT NULL,
    valor          DECIMAL(10,2) NOT NULL,
    motivo         VARCHAR(150)  NULL,
    data_movimento DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_movimento),
    KEY fk_movimento_caixa (id_caixa),
    CONSTRAINT fk_movimento_caixa FOREIGN KEY (id_caixa) REFERENCES caixa (id_caixa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
