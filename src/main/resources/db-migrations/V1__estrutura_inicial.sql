-- V1: estrutura do banco casarural como estava em 10/10/2026.
--
-- Num banco vazio (computador da loja), cria todas as tabelas.
-- Num banco que já tem as tabelas (desenvolvimento), o Flyway registra esta versão
-- como baseline e NÃO a executa.
--
-- REGRA: depois de aplicada, esta migração nunca deve ser editada.
-- Qualquer mudança na estrutura vira um arquivo novo: V2__descricao.sql, V3__...

CREATE TABLE cliente (
    id_cliente     INT           NOT NULL AUTO_INCREMENT,
    nome_cliente   VARCHAR(20)   NOT NULL,
    limite_credito DECIMAL(10,2) NOT NULL,
    situacao_fiado VARCHAR(20)   NOT NULL,
    PRIMARY KEY (id_cliente),
    UNIQUE KEY id_cliente (id_cliente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE produto (
    id_produto    INT           NOT NULL AUTO_INCREMENT,
    cod_barras    VARCHAR(60)   NOT NULL,
    nome_produto  VARCHAR(30)   NOT NULL,
    preco_produto DECIMAL(10,2) NOT NULL,
    estoque       INT           NOT NULL,
    PRIMARY KEY (id_produto),
    UNIQUE KEY id_produto (id_produto),
    UNIQUE KEY uk_cod_barras (cod_barras)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pedido (
    id_pedido   INT           NOT NULL AUTO_INCREMENT,
    id_cliente  INT           DEFAULT NULL,
    data_pedido DATETIME      DEFAULT CURRENT_TIMESTAMP,
    valor_total DECIMAL(10,2) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    PRIMARY KEY (id_pedido),
    UNIQUE KEY id_pedido (id_pedido),
    KEY Pedido_fk1 (id_cliente),
    CONSTRAINT Pedido_fk1 FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE itempedido (
    id_item        INT           NOT NULL AUTO_INCREMENT,
    id_produto     INT           NOT NULL,
    quantidade     INT           NOT NULL,
    preco_unitario DECIMAL(10,2) NOT NULL,
    id_pedido      INT           NOT NULL,
    PRIMARY KEY (id_item),
    UNIQUE KEY id_item (id_item),
    KEY ItemPedido_fk1 (id_produto),
    KEY ItemPedido_fk4 (id_pedido),
    CONSTRAINT ItemPedido_fk1 FOREIGN KEY (id_produto) REFERENCES produto (id_produto),
    CONSTRAINT ItemPedido_fk4 FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pagamento (
    id_pagamento     INT           NOT NULL AUTO_INCREMENT,
    id_pedido        INT           NOT NULL,
    valor_pago       DECIMAL(10,2) NOT NULL,
    data_pagamento   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    metodo_pagamento VARCHAR(10)   DEFAULT NULL,
    PRIMARY KEY (id_pagamento),
    UNIQUE KEY id_pagamento (id_pagamento),
    KEY Pagamento_fk1 (id_pedido),
    CONSTRAINT Pagamento_fk1 FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fornecimento (
    id_fornecimento   INT  NOT NULL AUTO_INCREMENT,
    id_produto        INT  NOT NULL,
    quantidade        INT  NOT NULL,
    data_fornecimento DATE NOT NULL,
    PRIMARY KEY (id_fornecimento),
    UNIQUE KEY id_fornecimento (id_fornecimento),
    KEY Fornecimento_fk1 (id_produto),
    CONSTRAINT Fornecimento_fk1 FOREIGN KEY (id_produto) REFERENCES produto (id_produto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
