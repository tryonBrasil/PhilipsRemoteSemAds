package com.example.philipsremote;

/** Atualização do app. Na versão "github" baixa o APK da última Release; na versão "play" não faz nada (a Play atualiza). */
public interface Updater {
    void verificarAoAbrir();
    void aoRetornarDoSistema();
    void verificarManualmente();
    void destroy();
}