-- V12: relatórios (PIN) e controle da compra de pão.

-- Configurações do sistema, chave/valor. Primeiro uso: o PIN dos relatórios (guardado cifrado)
-- e qual produto é o pão do relatório "Pão do dia".
CREATE TABLE configuracao (
    chave VARCHAR(50)  NOT NULL,
    valor VARCHAR(255) NOT NULL,
    PRIMARY KEY (chave)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Quantos pães chegaram em cada turno (manhã 7h, tarde 15h). Uma linha por dia e turno:
-- registrar de novo o mesmo turno corrige a quantidade.
CREATE TABLE compra_pao (
    id_compra_pao INT         NOT NULL AUTO_INCREMENT,
    id_produto    INT         NOT NULL,
    data_compra   DATE        NOT NULL,
    turno         VARCHAR(10) NOT NULL,
    quantidade    INT         NOT NULL,
    registrado_em DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_compra_pao),
    UNIQUE KEY uk_compra_pao_dia_turno (id_produto, data_compra, turno),
    CONSTRAINT fk_compra_pao_produto FOREIGN KEY (id_produto) REFERENCES produto (id_produto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Pão francês com código curto "1": R$ 0,60 a unidade, sem controle de estoque.
-- Só cria se o código 1 ainda estiver livre.
INSERT INTO produto (cod_barras, nome_produto, preco_produto, estoque, permite_fiado, tipo_venda, controla_estoque)
SELECT '1', 'PÃO FRANCÊS', 0.60, 0, TRUE, 'UNIDADE', FALSE
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM produto WHERE cod_barras = '1');

-- O relatório do pão usa o produto de código 1
INSERT INTO configuracao (chave, valor)
SELECT 'pao.id_produto', CAST(id_produto AS CHAR) FROM produto WHERE cod_barras = '1';
