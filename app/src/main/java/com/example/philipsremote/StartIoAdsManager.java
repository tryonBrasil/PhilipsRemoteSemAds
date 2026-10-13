package com.example.philipsremote;

import android.app.Activity;
import android.util.Log;

import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;

/** Start.io interstitials; shown only at explicit navigation points, never on IR key presses. */
public final class StartIoAdsManager {
    private static final String TAG = "StartIoAds";
    private static final String APP_ID = "209418154";

    private final Activity activity;
    private final StartAppAd interstitial;
    private boolean ready = false;

    public StartIoAdsManager(Activity activity) {
        this.activity = activity;
        this.interstitial = new StartAppAd(activity);
        StartAppSDK.initParams(activity, APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback(() -> {
                    // Test ads remain enabled until the integration is validated.
                    StartAppSDK.setTestAdsEnabled(true);
                    load();
                })
                .init();
    }

    private void load() {
        if (activity.isFinishing()) return;
        interstitial.loadAd(new AdEventListener() {
            @Override public void onReceiveAd(Ad ad) {
                ready = true;
                Log.d(TAG, "Start.io interstitial ready");
            }

            @Override public void onFailedToReceiveAd(Ad ad) {
                ready = false;
                Log.w(TAG, "Start.io interstitial unavailable");
            }
        });
    }

    public void showIfReady() {
        if (activity.isFinishing() || !ready) return;
        ready = false;
        try {
            interstitial.showAd();
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not show interstitial", e);
        } finally {
            load();
        }
    }
}
