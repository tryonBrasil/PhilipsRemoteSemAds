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
    private static final String APP_ID = "209319090";

    private final Activity activity;
    private StartAppAd interstitial;
    private boolean sdkReady = false;
    private boolean ready = false;
    private boolean loading = false;

    public StartIoAdsManager(Activity activity) {
        this.activity = activity;

        // Use the SDK initialization callback: do not request ads until Start.io has
        // completed initialization and registered the app/product ID.
        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG);
        Log.i(TAG, "Initializing Start.io; appId=" + APP_ID
                + ", package=" + activity.getPackageName()
                + ", debugBuild=" + BuildConfig.DEBUG);

        StartAppSDK.initParams(activity, APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback(new Runnable() {
                    @Override public void run() {
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing()) return;
                            if (sdkReady) return;
                            sdkReady = true;
                            Log.i(TAG, "Start.io SDK initialization callback completed; appId="
                                    + APP_ID + ", package=" + activity.getPackageName());
                            interstitial = new StartAppAd(activity);
                            load();
                        });
                    }
                })
                .init();
    }

    private void load() {
        if (activity.isFinishing() || !sdkReady || interstitial == null || loading || ready) return;
        loading = true;
        Log.i(TAG, "Requesting Start.io interstitial after SDK initialization");
        interstitial.loadAd(new AdEventListener() {
            @Override public void onReceiveAd(Ad ad) {
                loading = false;
                ready = true;
                Log.i(TAG, "Start.io interstitial loaded successfully; available for the next eligible navigation");
            }

            @Override public void onFailedToReceiveAd(Ad ad) {
                loading = false;
                ready = false;
                Log.e(TAG, "Start.io interstitial failed to load: "
                        + (ad == null ? "ad result is null" : ad.getErrorMessage()));
                // Do not immediately loop loadAd() here; retry on the next eligible navigation.
            }
        });
    }

    public void showIfReady() {
        if (activity.isFinishing()) return;
        if (!sdkReady || interstitial == null) {
            Log.d(TAG, "SDK is not ready; skipping this navigation without a delayed display");
            return;
        }
        if (!ready) {
            load();
            Log.d(TAG, "Interstitial is not ready; preparing it for a future eligible navigation");
            return;
        }
        showReadyAd();
    }

    private void showReadyAd() {
        if (activity.isFinishing() || !sdkReady || interstitial == null || !ready) return;
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
                    Log.e(TAG, "Start.io loaded an ad but could not display it: "
                            + (ad == null ? "ad result is null" : ad.getErrorMessage()));
                    load();
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception while displaying Start.io interstitial", e);
            load();
        }
    }
}
