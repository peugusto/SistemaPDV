-- V5: caixa (um por dia de trabalho, aberto com o troco inicial e fechado no fim do dia).
--
-- valor_esperado: dinheiro que deveria haver na gaveta no fechamento, calculado pelo sistema
--                 (troco + vendas e recebimentos em dinheiro + reforços - sangrias)
-- valor_contado:  dinheiro contado na gaveta; NULL = fechado sem conferência
-- aberto:         coluna calculada (1 enquanto o caixa está aberto, NULL depois). O índice único
--                 nela garante, no próprio banco, que nunca existam dois caixas abertos ao mesmo tempo.

CREATE TABLE caixa (
    id_caixa       INT           NOT NULL AUTO_INCREMENT,
    aberto_em      DATETIME      NOT NULL,
    valor_inicial  DECIMAL(10,2) NOT NULL,
    fechado_em     DATETIME      NULL,
    valor_esperado DECIMAL(10,2) NULL,
    valor_contado  DECIMAL(10,2) NULL,
    observacao     VARCHAR(255)  NULL,
    aberto         TINYINT GENERATED ALWAYS AS (IF(fechado_em IS NULL, 1, NULL)) STORED,
    PRIMARY KEY (id_caixa),
    UNIQUE KEY uk_um_caixa_aberto (aberto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
