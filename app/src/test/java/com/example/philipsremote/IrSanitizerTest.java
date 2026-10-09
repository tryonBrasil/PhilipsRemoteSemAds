package com.example.philipsremote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IrSanitizerTest {
    @Test public void aceitaSinalNormal() {
        assertTrue(IrSanitizer.valido(38000, IrEncoder.nec(0x04, 0x08)));
        assertTrue(IrSanitizer.valido(36000, IrEncoder.rc6(0, 12, true)));
        assertTrue(IrSanitizer.valido(38000, IrEncoder.coolix(0xB27BE0)));
    }

    @Test public void rejeitaFrequenciaForaDosLimites() {
        int[] p = {9000, 4500, 560};
        assertFalse(IrSanitizer.valido(0, p));
        assertFalse(IrSanitizer.valido(-1, p));
        assertFalse(IrSanitizer.valido(1000, p));
        assertFalse(IrSanitizer.valido(500000, p));
    }

    @Test public void rejeitaPadraoInvalido() {
        assertFalse(IrSanitizer.padraoValido(null));
        assertFalse(IrSanitizer.padraoValido(new int[0]));
        assertFalse(IrSanitizer.padraoValido(new int[]{100, -5, 100}));
        assertFalse(IrSanitizer.padraoValido(new int[]{100, IrSanitizer.DURACAO_MAX_US + 1}));
        assertFalse(IrSanitizer.padraoValido(new int[IrSanitizer.MAX_DURACOES + 1]));
    }
}