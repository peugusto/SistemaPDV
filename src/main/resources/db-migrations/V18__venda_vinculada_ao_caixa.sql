-- V18: vínculo da venda com a sessão do caixa, inclusive vendas de valor zero.
-- A impressão não deve adivinhar o caixa pela data nem duplicar vendas com pagamento misto.
ALTER TABLE pedido
    ADD COLUMN id_caixa INT NULL,
    ADD KEY idx_pedido_caixa (id_caixa),
    ADD CONSTRAINT fk_pedido_caixa FOREIGN KEY (id_caixa) REFERENCES caixa (id_caixa);

-- Recupera somente vínculos históricos inequívocos pelos pagamentos ORIGINAIS da venda.
-- Recebimentos posteriores de fiado pertencem ao caixa do recebimento, não ao da compra.
UPDATE pedido p
JOIN (
    SELECT id_pedido, MIN(id_caixa) AS id_caixa
    FROM (
        SELECT id_pedido, id_caixa FROM pagamento WHERE origem = 'VENDA' AND id_caixa IS NOT NULL
        UNION ALL
        SELECT id_pedido, id_caixa FROM pagamento_estornado WHERE origem = 'VENDA' AND id_caixa IS NOT NULL
    ) pagamentos_originais
    GROUP BY id_pedido
    HAVING COUNT(DISTINCT id_caixa) = 1
) historico ON historico.id_pedido = p.id_pedido
JOIN caixa cx ON cx.id_caixa = historico.id_caixa
SET p.id_caixa = historico.id_caixa;
