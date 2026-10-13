package com.example.philipsremote;

import android.app.Activity;
import android.util.Log;

import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;

/** Start.io interstitials; shown only at explicit navigation points, never on IR key presses. */
public final class StartIoAdsManager {
    private static final String TAG = "StartIoAds";
    private static final String APP_ID = "209418154";

    private final Activity activity;
    private final StartAppAd interstitial;
    private boolean ready = false;
    private boolean loading = false;
    private boolean pendingShow = false;

    public StartIoAdsManager(Activity activity) {
        this.activity = activity;
        this.interstitial = new StartAppAd(activity);
        StartAppSDK.initParams(activity, APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback(() -> {
                    // Use test inventory only in debug builds; production release uses real inventory.
                    StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG);
                    load();
                })
                .init();
    }

    private void load() {
        if (activity.isFinishing() || loading || ready) return;
        loading = true;
        interstitial.loadAd(new AdEventListener() {
            @Override public void onReceiveAd(Ad ad) {
                loading = false;
                ready = true;
                Log.d(TAG, "Start.io interstitial ready");
                // If the user reached an eligible transition while the ad was loading,
                // display it now instead of silently missing that transition.
                if (pendingShow) showReadyAd();
            }

            @Override public void onFailedToReceiveAd(Ad ad) {
                loading = false;
                ready = false;
                // A later navigation request will retry loading if no ad is ready.
                Log.w(TAG, "Start.io interstitial unavailable; will retry on next eligible navigation");
            }
        });
    }

    public void showIfReady() {
        if (activity.isFinishing()) return;
        if (!ready) {
            // Remember this eligible transition. If loading finishes asynchronously,
            // show the ad then rather than requiring the user to navigate a second time.
            pendingShow = true;
            load();
            Log.d(TAG, "Interstitial not ready; queued display after load");
            return;
        }
        showReadyAd();
    }

    private void showReadyAd() {
        if (activity.isFinishing() || !ready) return;
        pendingShow = false;
        ready = false;
        try {
            interstitial.showAd(new AdDisplayListener() {
                @Override public void adDisplayed(Ad ad) {
                    Log.d(TAG, "Start.io interstitial displayed");
                }

                @Override public void adHidden(Ad ad) {
                    Log.d(TAG, "Start.io interstitial closed");
                    load();
                }

                @Override public void adClicked(Ad ad) {
                    Log.d(TAG, "Start.io interstitial clicked");
                }

                @Override public void adNotDisplayed(Ad ad) {
                    Log.w(TAG, "Start.io interstitial could not be displayed");
                    load();
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not show interstitial", e);
            load();
        }
    }
}
