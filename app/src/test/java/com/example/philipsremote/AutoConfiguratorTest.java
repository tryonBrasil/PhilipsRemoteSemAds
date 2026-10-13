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
        assertTrue(AutoConfigurator.tabela("Philips / RC5").isEmpty());
        assertFalse(AutoConfigurator.tabelaCandidata("LG / NEC"));
        assertFalse(AutoConfigurator.tabelaCandidata("Philips / RC6"));
    }

    @Test public void samsungPreencheCodigosPrincipaisEMarcaComoCandidato() {
        Map<String, Integer> tabela = AutoConfigurator.tabela("Samsung TV");
        assertEquals(Integer.valueOf(0xE040), tabela.get("POWER"));
        assertEquals(Integer.valueOf(0xE0E0), tabela.get("VOL_UP"));
        assertEquals(Integer.valueOf(0xE0D0), tabela.get("VOL_DOWN"));
        assertEquals(Integer.valueOf(0xE020), tabela.get("NUM_1"));
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

    @Test public void ventiladorSoRecebeAutoConfigQuandoModeloKxd194() {
        Map<String, Integer> kxd = AutoConfigurator.tabelaVentilador("KXD194", "EX Decor 40w", "");
        assertEquals(Integer.valueOf(0x0418), kxd.get("POWER"));
        assertEquals(1, kxd.size());
        assertTrue(AutoConfigurator.tabelaVentilador("Ventilador", "Universal", "").isEmpty());
    }

    @Test public void protocolosSemTabelaConfirmadaContinuamGuiados() {
        assertTrue(AutoConfigurator.tabela("Philips / RC5").isEmpty());
        assertTrue(AutoConfigurator.tabela("AOC / NEC").isEmpty());
        assertTrue(AutoConfigurator.tabela("TCL / NEC").isEmpty());
        assertTrue(AutoConfigurator.tabela("Panasonic TV").isEmpty());
        assertTrue(AutoConfigurator.tabela("AC Midea").isEmpty());
        assertFalse(AutoConfigurator.tabelaCandidata("Panasonic TV"));
    }
}
