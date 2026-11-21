package com.example.philipsremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateIntegrityTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test public void extraiDigestPublicadoPeloGitHub() {
        assertEquals(HASH, UpdateIntegrity.extrairSha256("sha256:" + HASH));
        assertEquals(HASH, UpdateIntegrity.extrairSha256("SHA256:" + HASH.toUpperCase()));
    }

    @Test public void rejeitaDigestAusenteOuMalformado() {
        assertEquals("", UpdateIntegrity.extrairSha256(""));
        assertEquals("", UpdateIntegrity.extrairSha256(HASH));
        assertEquals("", UpdateIntegrity.extrairSha256("sha256:1234"));
        assertEquals("", UpdateIntegrity.extrairSha256("sha1:" + HASH));
    }

    @Test public void exigeCorrespondenciaExata() {
        assertTrue(UpdateIntegrity.corresponde(HASH, HASH.toUpperCase()));
        assertFalse(UpdateIntegrity.corresponde("", HASH));
        assertFalse(UpdateIntegrity.corresponde(HASH, ""));
        assertFalse(UpdateIntegrity.corresponde(HASH, HASH.substring(1)));
        assertFalse(UpdateIntegrity.corresponde(HASH, "f" + HASH.substring(1)));
    }
}
