package com.example.philipsremote;

import android.app.Activity;

/** Versão "play": a Google Play atualiza o app; a política da Play proíbe instalar APK por conta própria. */
final class UpdaterFactory {
    private UpdaterFactory() {}

    static Updater criar(Activity activity) {
        return new Updater() {
            @Override public void verificarAoAbrir() {}
            @Override public void aoRetornarDoSistema() {}
            @Override public void verificarManualmente() {}
            @Override public void destroy() {}
        };
    }
}