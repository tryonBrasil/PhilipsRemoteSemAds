package com.example.philipsremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionUtilTest {
    @Test public void maiorMenorIgual() {
        assertTrue(VersionUtil.comparar("1.3.10", "1.3.9") > 0);
        assertTrue(VersionUtil.comparar("1.2.13", "1.3.0") < 0);
        assertEquals(0, VersionUtil.comparar("1.3", "1.3.0"));
    }
    @Test public void ignoraSufixoELixo() {
        assertEquals(0, VersionUtil.comparar("1.3.5-beta", "1.3.5"));
        assertTrue(VersionUtil.comparar("2", "1.9.9") > 0);
        assertEquals(0, VersionUtil.comparar("abc", "0.0.0"));
    }
}