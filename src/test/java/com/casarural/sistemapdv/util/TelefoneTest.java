package com.casarural.sistemapdv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelefoneTest {

    @Test
    void guardaSoOsNumerosDeQualquerJeitoQueForDigitado() {
        assertEquals("11987654321", Telefone.normalizar("(11) 98765-4321"));
        assertEquals("11987654321", Telefone.normalizar("11 98765 4321"));
        assertEquals("11987654321", Telefone.normalizar("11987654321"));
        assertEquals("1133334444", Telefone.normalizar("(11) 3333-4444"));
    }

    @Test
    void campoVazioEhPermitido() {
        assertNull(Telefone.normalizar(null));
        assertNull(Telefone.normalizar("   "));
    }

    @Test
    void numeroSemDddOuIncompletoEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> Telefone.normalizar("98765-4321"));
        assertThrows(IllegalArgumentException.class, () -> Telefone.normalizar("(11) 9876"));
        assertThrows(IllegalArgumentException.class, () -> Telefone.normalizar("119876543210"));
    }

    @Test
    void mostraFormatado() {
        assertEquals("(11) 98765-4321", Telefone.formatar("11987654321"));
        assertEquals("(11) 3333-4444", Telefone.formatar("1133334444"));
        assertEquals("", Telefone.formatar(null));
    }
}
