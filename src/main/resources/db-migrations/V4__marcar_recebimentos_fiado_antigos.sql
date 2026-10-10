-- V4: marca como RECEBIMENTO_FIADO os pagamentos antigos que abateram dívida.
--
-- Antes da V3 não havia como distinguir. Regra usada: pagamento que não é FIADO, num pedido
-- que teve parte no fiado, feito mais de 1 minuto depois da compra. Os pagamentos feitos na
-- hora da venda são gravados no mesmo segundo do pedido.
-- Num banco novo (computador da loja) não há dados antigos e nada é alterado.

UPDATE pagamento pg
JOIN pedido p ON p.id_pedido = pg.id_pedido
JOIN (SELECT DISTINCT id_pedido FROM pagamento WHERE metodo_pagamento = 'FIADO') f
     ON f.id_pedido = pg.id_pedido
SET pg.origem = 'RECEBIMENTO_FIADO'
WHERE pg.metodo_pagamento <> 'FIADO'
  AND TIMESTAMPDIFF(SECOND, p.data_pedido, pg.data_pagamento) > 60;
