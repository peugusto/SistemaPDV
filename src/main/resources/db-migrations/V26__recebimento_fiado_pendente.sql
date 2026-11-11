-- A trava acompanha o commit do recebimento e sobrevive ao fechamento do PDV.
-- Removida quando o operador toma ciência do recebimento confirmado.
CREATE TABLE recebimento_fiado_pendente (
    id_cliente INT NOT NULL PRIMARY KEY,
    id_recebimento INT NOT NULL,
    CONSTRAINT fk_recebimento_pendente_cliente FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
) ENGINE=InnoDB;
