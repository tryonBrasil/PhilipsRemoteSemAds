package com.example.philipsremote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MonetizationLimitTest {
    @Test public void planoGratisPermiteSomenteUmControle() {
        assertTrue(MonetizationManager.podeSalvar(0, false));
        assertFalse(MonetizationManager.podeSalvar(1, false));
        assertFalse(MonetizationManager.podeSalvar(2, false));
    }

    @Test public void premiumPermiteControlesIlimitados() {
        assertTrue(MonetizationManager.podeSalvar(0, true));
        assertTrue(MonetizationManager.podeSalvar(1, true));
        assertTrue(MonetizationManager.podeSalvar(100, true));
    }

    @Test public void quantidadeNegativaNaoBloqueiaPrimeiroControle() {
        assertTrue(MonetizationManager.podeSalvar(-1, false));
    }
}
