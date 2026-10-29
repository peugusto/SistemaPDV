package com.casarural.sistemapdv.model.entities;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EntradaEstoqueTest {
    @Test
    void recusaIdentificacaoQuantidadeECustoAntesDeQualquerDao() {
        UUID chave = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new EntradaEstoque(null, 1, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new EntradaEstoque(chave, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new EntradaEstoque(chave, 1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new EntradaEstoque(chave, 1, -5, null));
        for (String custo : new String[]{"-0.01", "100000000", "1.001"}) {
            assertThrows(IllegalArgumentException.class, () -> new EntradaEstoque(chave, 1, 5, new BigDecimal(custo)));
        }
    }

    @Test
    void custoVazioNaoAtualizaECustoValidoTemComparacaoCanonica() {
        UUID chave = UUID.randomUUID();
        EntradaEstoque manter = new EntradaEstoque(chave, 1, 5, null);
        assertNull(manter.getCustoInformado());
        assertTrue(manter.mesmosDados(1, 5, null));
        assertFalse(manter.mesmosDados(2, 5, null));
        assertFalse(manter.mesmosDados(1, 6, null));
        EntradaEstoque mudar = new EntradaEstoque(chave, 1, 5, new BigDecimal("4.5"));
        assertEquals(new BigDecimal("4.50"), mudar.getCustoInformado());
        assertTrue(mudar.mesmosDados(1, 5, new BigDecimal("4.500")));
        assertFalse(mudar.mesmosDados(1, 5, null));
        assertEquals(new BigDecimal("99999999.99"),
                new EntradaEstoque(chave, 1, Integer.MAX_VALUE, new BigDecimal("99999999.99")).getCustoInformado());
    }
}
