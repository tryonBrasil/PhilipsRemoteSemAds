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

        // Configure test inventory before initialization, and initialize the SDK before
        // creating StartAppAd, following Start.io's documented initialization sequence.
        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG);
        Log.i(TAG, "Initializing Start.io; appId=" + APP_ID
                + ", package=" + activity.getPackageName()
                + ", debugBuild=" + BuildConfig.DEBUG);
        StartAppSDK.init(activity, APP_ID);

        this.interstitial = new StartAppAd(activity);
        load();
    }

    private void load() {
        if (activity.isFinishing() || loading || ready) return;
        loading = true;
        Log.d(TAG, "Requesting Start.io interstitial");
        interstitial.loadAd(new AdEventListener() {
            @Override public void onReceiveAd(Ad ad) {
                loading = false;
                ready = true;
                Log.i(TAG, "Start.io interstitial loaded successfully");
                if (pendingShow) showReadyAd();
            }

            @Override public void onFailedToReceiveAd(Ad ad) {
                loading = false;
                ready = false;
                Log.e(TAG, "Start.io interstitial failed to load. Ad result=" + String.valueOf(ad)
                        + ". Check the Start.io SDK log immediately before this message for the failure reason.");
                // Retry on the next eligible navigation; do not loop requests continuously.
            }
        });
    }

    public void showIfReady() {
        if (activity.isFinishing()) return;
        if (!ready) {
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
                    Log.i(TAG, "Start.io interstitial displayed");
                }

                @Override public void adHidden(Ad ad) {
                    Log.d(TAG, "Start.io interstitial closed; requesting next ad");
                    load();
                }

                @Override public void adClicked(Ad ad) {
                    Log.d(TAG, "Start.io interstitial clicked");
                }

                @Override public void adNotDisplayed(Ad ad) {
                    Log.e(TAG, "Start.io loaded an ad but could not display it: " + String.valueOf(ad));
                    load();
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception while displaying Start.io interstitial", e);
            load();
        }
    }
}
