package com.example.philipsremote;

import android.app.Activity;

/** Versão "github": atualização por APK das Releases do GitHub. */
final class UpdaterFactory {
    private UpdaterFactory() {}

    static Updater criar(Activity activity) {
        return new UpdateManager(activity);
    }
}