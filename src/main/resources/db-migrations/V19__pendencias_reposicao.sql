-- V19: a lista acumula saídas reais e só diminui com entradas/cancelamentos.
ALTER TABLE produto
    ADD COLUMN quantidade_repor BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_produto_quantidade_repor CHECK (quantidade_repor >= 0);

-- Saldo inicial: preserva a sugestão da lista anterior na data da atualização.
-- Depois desta migração não existe expiração por janela de sete dias.
UPDATE produto pr
LEFT JOIN (
    SELECT ip.id_produto, SUM(ip.quantidade) AS vendido
    FROM itempedido ip JOIN pedido p ON p.id_pedido = ip.id_pedido
    WHERE p.status NOT IN ('EM_ANDAMENTO', 'CANCELADO')
      AND p.data_pedido >= CURRENT_DATE - INTERVAL 6 DAY
      AND p.data_pedido < CURRENT_DATE + INTERVAL 1 DAY
    GROUP BY ip.id_produto
) v ON v.id_produto = pr.id_produto
SET pr.quantidade_repor = GREATEST(0, COALESCE(v.vendido, 0) - GREATEST(0, pr.estoque))
WHERE pr.ativo AND pr.controla_estoque AND pr.tipo_venda <> 'PESAVEL';
