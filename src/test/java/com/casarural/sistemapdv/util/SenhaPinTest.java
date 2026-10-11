package com.casarural.sistemapdv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** PIN dos relatórios guardado como hash. */
class SenhaPinTest {

    @Test
    void formato() {
        assertTrue(SenhaPin.formatoValido("1234"));
        assertTrue(SenhaPin.formatoValido("123456"));
        assertFalse(SenhaPin.formatoValido("123"));
        assertFalse(SenhaPin.formatoValido("1234567"));
        assertFalse(SenhaPin.formatoValido("12a4"));
        assertFalse(SenhaPin.formatoValido(null));
    }

    @Test
    void confereOPinCertoERecusaOErrado() {
        String guardado = SenhaPin.gerarHash("4821");
        assertTrue(SenhaPin.confere("4821", guardado));
        assertFalse(SenhaPin.confere("4822", guardado));
        assertFalse(SenhaPin.confere("", guardado));
    }

    @Test
    void naoGuardaOPinEMesmoPinGeraHashDiferente() {
        String a = SenhaPin.gerarHash("4821");
        String b = SenhaPin.gerarHash("4821");
        assertFalse(a.contains("4821"));
        assertNotEquals(a, b); // sal diferente
        assertTrue(a.startsWith("pbkdf2$"));
    }

    @Test
    void valorGuardadoEstragadoNaoLibera() {
        assertFalse(SenhaPin.confere("4821", "qualquer coisa"));
        assertFalse(SenhaPin.confere("4821", "pbkdf2$abc$###$###"));
        assertFalse(SenhaPin.confere("4821", null));
    }

    @Test
    void pinInvalidoNaoGeraHash() {
        assertThrows(IllegalArgumentException.class, () -> SenhaPin.gerarHash("12"));
    }
}
