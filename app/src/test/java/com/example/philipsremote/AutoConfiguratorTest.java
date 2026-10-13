package com.example.philipsremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class AutoConfiguratorTest {
    @Test public void philipsELgPreenchemTodasAsFuncoes() {
        assertEquals(RemoteKeys.CHAVES.length, AutoConfigurator.tabela("LG / NEC").size());
        assertEquals(RemoteKeys.CHAVES.length, AutoConfigurator.tabela("Philips / RC6").size());
        assertEquals(RemoteKeys.CHAVES.length, AutoConfigurator.tabela("Philips / RC5").size());
        assertFalse(AutoConfigurator.tabelaCandidata("LG / NEC"));
        assertFalse(AutoConfigurator.tabelaCandidata("Philips / RC6"));
    }

    @Test public void samsungPreencheCodigosPrincipaisEMarcaComoCandidato() {
        Map<String, Integer> tabela = AutoConfigurator.tabela("Samsung TV");
        assertEquals(Integer.valueOf(0x0702), tabela.get("POWER"));
        assertEquals(Integer.valueOf(0x0707), tabela.get("VOL_UP"));
        assertEquals(Integer.valueOf(0x070B), tabela.get("VOL_DOWN"));
        assertEquals(Integer.valueOf(0x0704), tabela.get("NUM_1"));
        assertTrue(AutoConfigurator.tabelaCandidata("Samsung TV"));
    }

    @Test public void sonyPreencheCodigosPrincipaisEMarcaComoCandidato() {
        Map<String, Integer> tabela = AutoConfigurator.tabela("Sony TV");
        assertEquals(Integer.valueOf(0x0115), tabela.get("POWER"));
        assertEquals(Integer.valueOf(0x0112), tabela.get("VOL_UP"));
        assertEquals(Integer.valueOf(0x0174), tabela.get("UP"));
        assertEquals(Integer.valueOf(0x0101), tabela.get("NUM_1"));
        assertTrue(AutoConfigurator.tabelaCandidata("Sony TV"));
    }

    @Test public void protocolosSemTabelaConfirmadaContinuamGuiados() {
        assertTrue(AutoConfigurator.tabela("AOC / NEC").isEmpty());
        assertTrue(AutoConfigurator.tabela("TCL / NEC").isEmpty());
        assertTrue(AutoConfigurator.tabela("Panasonic TV").isEmpty());
        assertTrue(AutoConfigurator.tabela("AC Midea").isEmpty());
        assertFalse(AutoConfigurator.tabelaCandidata("Panasonic TV"));
    }
}
