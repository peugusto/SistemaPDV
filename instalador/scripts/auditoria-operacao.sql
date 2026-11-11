-- Executado pelo instalador como root, depois do Flyway. Apenas identificadores
-- tecnicos e dados da operacao. Nomes, motivos, telefones e segredos ficam de fora.
USE casarural;

CREATE TABLE IF NOT EXISTS auditoria_operacao (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ocorrido_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    conta_banco VARCHAR(288) NOT NULL,
    evento VARCHAR(40) NOT NULL,
    id_registro BIGINT NULL,
    detalhe JSON NULL,
    KEY idx_auditoria_data (ocorrido_em, id)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER IF NOT EXISTS auditoria_desconto AFTER INSERT ON pedido FOR EACH ROW
BEGIN
    IF NEW.desconto > 0 THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'VENDA_COM_DESCONTO', NEW.id_pedido, JSON_OBJECT('desconto', NEW.desconto));
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS auditoria_cancelamento AFTER UPDATE ON pedido FOR EACH ROW
BEGIN
    IF OLD.status <> 'CANCELADO' AND NEW.status = 'CANCELADO' THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'VENDA_CANCELADA', NEW.id_pedido, JSON_OBJECT('total', NEW.valor_total));
    END IF;
    IF NEW.id_pedido_troca IS NOT NULL AND NOT (OLD.id_pedido_troca <=> NEW.id_pedido_troca) THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'VENDA_TROCADA', NEW.id_pedido, JSON_OBJECT('novo_pedido', NEW.id_pedido_troca));
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS auditoria_recebimento AFTER UPDATE ON pagamento FOR EACH ROW
BEGIN
    IF NEW.origem = 'RECEBIMENTO_FIADO' AND OLD.id_recebimento IS NULL
        AND NEW.id_recebimento = NEW.id_pagamento THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'FIADO_RECEBIDO', NEW.id_recebimento, JSON_OBJECT('caixa', NEW.id_caixa));
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS auditoria_estorno AFTER INSERT ON pagamento_estornado FOR EACH ROW
BEGIN
    IF NEW.tipo = 'ESTORNO_RECEBIMENTO' THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'FIADO_ESTORNADO', NEW.id_pagamento,
                JSON_OBJECT('recebimento', NEW.id_recebimento, 'pedido', NEW.id_pedido, 'valor', NEW.valor_pago));
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS auditoria_movimento_caixa AFTER INSERT ON movimento_caixa FOR EACH ROW
INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
VALUES (USER(), 'MOVIMENTO_CAIXA', NEW.id_movimento,
        JSON_OBJECT('caixa', NEW.id_caixa, 'tipo', NEW.tipo, 'valor', NEW.valor))$$

CREATE TRIGGER IF NOT EXISTS auditoria_fechamento AFTER UPDATE ON caixa FOR EACH ROW
BEGIN
    IF OLD.fechado_em IS NULL AND NEW.fechado_em IS NOT NULL THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'CAIXA_FECHADO', NEW.id_caixa,
                JSON_OBJECT('esperado', NEW.valor_esperado, 'contado', NEW.valor_contado));
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS auditoria_credito AFTER UPDATE ON cliente FOR EACH ROW
BEGIN
    IF NOT (OLD.limite_credito <=> NEW.limite_credito) OR NOT (OLD.situacao_fiado <=> NEW.situacao_fiado) THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'CREDITO_ALTERADO', NEW.id_cliente,
                JSON_OBJECT('limite_anterior', OLD.limite_credito, 'limite_novo', NEW.limite_credito,
                            'situacao_anterior', OLD.situacao_fiado, 'situacao_nova', NEW.situacao_fiado));
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS auditoria_produto AFTER UPDATE ON produto FOR EACH ROW
BEGIN
    IF NOT (OLD.preco_produto <=> NEW.preco_produto) OR NOT (OLD.preco_custo <=> NEW.preco_custo)
        OR OLD.ativo <> NEW.ativo OR OLD.permite_fiado <> NEW.permite_fiado THEN
        INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
        VALUES (USER(), 'PRODUTO_ALTERADO', NEW.id_produto,
                JSON_OBJECT('preco_anterior', OLD.preco_produto, 'preco_novo', NEW.preco_produto,
                            'custo_anterior', OLD.preco_custo, 'custo_novo', NEW.preco_custo,
                            'ativo_anterior', OLD.ativo, 'ativo_novo', NEW.ativo,
                            'fiado_anterior', OLD.permite_fiado, 'fiado_novo', NEW.permite_fiado));
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS auditoria_exclusao_produto AFTER DELETE ON produto FOR EACH ROW
INSERT INTO auditoria_operacao (conta_banco, evento, id_registro)
VALUES (USER(), 'PRODUTO_EXCLUIDO', OLD.id_produto)$$

CREATE TRIGGER IF NOT EXISTS auditoria_ajuste_estoque AFTER INSERT ON ajuste_estoque FOR EACH ROW
INSERT INTO auditoria_operacao (conta_banco, evento, id_registro, detalhe)
VALUES (USER(), 'ESTOQUE_AJUSTADO', NEW.id_ajuste,
        JSON_OBJECT('produto', NEW.id_produto, 'tipo', NEW.tipo, 'antes', NEW.saldo_antes, 'depois', NEW.saldo_depois))$$
DELIMITER ;
