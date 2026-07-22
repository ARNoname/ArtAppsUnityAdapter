package com.artapps.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

public class ArtAppsInterstitial {
    private static final String TAG = "ArtAppsInterstitial";

    private final String placementId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private InterstitialDelegate delegate;
    private boolean isReady;
    private String finalUrl;
    private String requestId;
    private String trackUrl;
    private int sessionGate;
    private boolean allow;
    private boolean didNotifyShown;
    private boolean didCompleteShow;

    public static InterstitialDelegate currentDelegate;
    public static ArtAppsInterstitial currentAd;

    public ArtAppsInterstitial(String placementId) {
        this.placementId = placementId;
    }

    public void setDelegate(InterstitialDelegate delegate) {
        this.delegate = delegate;
    }

    public boolean isReady() {
        return isReady;
    }

    public void load(final Context context) {
        resetForLoad();
        ArtApps sdk = ArtApps.getInstance();
        if (!sdk.isInitialized()) {
            if (delegate != null) {
                delegate.onAdFailedToLoad(this, 100, "SDK not initialized");
            }
            return;
        }

        if (!sdk.canShowAd(context)) {
            if (delegate != null) {
                delegate.onAdFailedToLoad(this, 205, "Frequency/Session Cap");
            }
            return;
        }

        ArtAppsNetworkManager.getInstance().fetchAd(
                context,
                sdk.getPartnerId(),
                sdk.getAppId(),
                placementId,
                new ArtAppsNetworkManager.AdResponseCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        handleLoadSuccess(response);
                    }

                    @Override
                    public void onError(Exception error) {
                        clearAdPayload();
                        if (delegate != null) {
                            delegate.onAdFailedToLoad(
                                    ArtAppsInterstitial.this,
                                    998,
                                    "Network error: " + error.getMessage()
                            );
                        }
                    }
                }
        );
    }

    public void show(final Activity activity) {
        if (!isReady) {
            notifyFailedToShow(301, "Ad not ready");
            return;
        }

        if (!isValidAdUrl(finalUrl)) {
            notifyFailedToShow(303, "Invalid ad URL");
            return;
        }

        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            notifyFailedToShow(304, "No presenting activity");
            return;
        }

        ArtAppsReachability.startMonitoring(activity.getApplicationContext());
        Log.d(TAG, "Interstitial show requested. Network: "
                + ArtAppsReachability.statusDescription());
        if (!ArtAppsReachability.isConnectedToNetwork(activity)) {
            notifyFailedToShow(305, "No internet connection");
            return;
        }

        currentDelegate = delegate;
        currentAd = this;
        didNotifyShown = false;
        didCompleteShow = false;
        isReady = false;

        final Intent intent = new Intent(activity, ArtAppsWebViewActivity.class);
        intent.putExtra("url", finalUrl);
        intent.putExtra("duration", sessionGate);
        intent.putExtra("requestId", requestId);
        intent.putExtra("trackUrl", trackUrl);

        Runnable presentation = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    notifyFailedToShow(304, "No presenting activity");
                    return;
                }

                try {
                    Log.d(TAG, "Presenting interstitial activity for placement: " + placementId);
                    activity.startActivity(intent);
                } catch (RuntimeException error) {
                    notifyFailedToShow(305, "Failed to present ad: " + error.getMessage());
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            presentation.run();
        } else {
            mainHandler.post(presentation);
        }
    }

    void notifyShown(Context context) {
        if (didNotifyShown || didCompleteShow) {
            return;
        }

        didNotifyShown = true;
        Log.d(TAG, "Display state: displayed");
        ArtApps.getInstance().didShowAd(context);
        InterstitialDelegate activeDelegate = activeDelegate();
        if (activeDelegate != null) {
            activeDelegate.onAdShown(this);
        }
    }

    void notifyHidden() {
        if (didCompleteShow) {
            return;
        }

        didCompleteShow = true;
        Log.d(TAG, "Display state: hidden");
        InterstitialDelegate activeDelegate = activeDelegate();
        if (activeDelegate != null) {
            activeDelegate.onAdHidden(this);
        }
        cleanupCurrentAdIfNeeded();
        clearAdPayload();
    }

    void notifyFailedToShow(int errorCode, String message) {
        if (didCompleteShow) {
            return;
        }

        didCompleteShow = true;
        String failureState = didNotifyShown ? "failure-after-display" : "failed-before-display";
        Log.d(TAG, "Display state: " + failureState + " (" + message + ")");
        InterstitialDelegate activeDelegate = activeDelegate();
        if (activeDelegate != null) {
            activeDelegate.onAdFailedToShow(this, errorCode, message);
        }
        cleanupCurrentAdIfNeeded();
        clearAdPayload();
    }

    private InterstitialDelegate activeDelegate() {
        return delegate != null ? delegate : currentDelegate;
    }

    private void handleLoadSuccess(JSONObject response) {
        try {
            allow = response.optBoolean("allow", false);
            int cooldown = response.optInt("cooldown_sec", -1);
            int resolvedSessionGate = response.optInt("session_gate", 20);
            int ttl = response.optInt("ttl", -1);
            ArtApps.getInstance().updateServerRestrictions(
                    cooldown >= 0 ? cooldown : null,
                    resolvedSessionGate,
                    ttl >= 0 ? ttl : null
            );

            if (!allow) {
                clearAdPayload();
                if (delegate != null) {
                    delegate.onAdFailedToLoad(this, 204, "No Fill");
                }
                return;
            }

            finalUrl = normalizeUrl(response.optString("final_url"));
            requestId = response.optString("request_id");
            trackUrl = response.optString("track_url");
            sessionGate = resolvedSessionGate;
            if (!isValidAdUrl(finalUrl)) {
                clearAdPayload();
                if (delegate != null) {
                    delegate.onAdFailedToLoad(this, 206, "Invalid ad response");
                }
                return;
            }

            isReady = true;
            if (delegate != null) {
                delegate.onAdLoaded(this);
            }
        } catch (RuntimeException error) {
            clearAdPayload();
            if (delegate != null) {
                delegate.onAdFailedToLoad(this, 999, "Parse error: " + error.getMessage());
            }
        }
    }

    private String normalizeUrl(String url) {
        return url == null ? null : url.trim();
    }

    private boolean isValidAdUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        String scheme = Uri.parse(url).getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private void resetForLoad() {
        clearAdPayload();
        didNotifyShown = false;
        didCompleteShow = false;
    }

    private void clearAdPayload() {
        isReady = false;
        finalUrl = null;
        requestId = null;
        trackUrl = null;
        sessionGate = 0;
        allow = false;
    }

    private void cleanupCurrentAdIfNeeded() {
        if (currentAd == this) {
            currentDelegate = null;
            currentAd = null;
        }
    }

    public interface InterstitialDelegate {
        void onAdLoaded(ArtAppsInterstitial ad);

        void onAdFailedToLoad(ArtAppsInterstitial ad, int errorCode, String message);

        void onAdShown(ArtAppsInterstitial ad);

        void onAdHidden(ArtAppsInterstitial ad);

        void onAdClicked(ArtAppsInterstitial ad);

        void onAdFailedToShow(ArtAppsInterstitial ad, int errorCode, String message);
    }
}
