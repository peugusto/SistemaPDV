-- Identidade de recebimentos novos: todas as parcelas usam o id da primeira parcela.
-- Registros antigos continuam NULL: horários coincidentes não permitem reconstruir a identidade.
ALTER TABLE pagamento ADD COLUMN id_recebimento INT NULL, ADD KEY idx_pagamento_recebimento (id_recebimento);
ALTER TABLE pagamento_estornado ADD COLUMN id_recebimento INT NULL, ADD KEY idx_estornado_recebimento (id_recebimento);

-- Conteúdo original preparado para impressão, preservado mesmo quando o envio falha.
CREATE TABLE comprovante_impressao (
    id_comprovante BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    referencia VARCHAR(50) NOT NULL,
    nome_trabalho VARCHAR(180) NOT NULL,
    texto LONGTEXT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_comprovante_origem (tipo, referencia),
    KEY idx_comprovante_tipo_id (tipo, id_comprovante)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
