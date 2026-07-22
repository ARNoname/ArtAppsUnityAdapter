package com.artapps.sdk;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.applovin.mediation.adapter.MaxAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.adapters.MediationAdapterBase;
import com.applovin.sdk.AppLovinSdk;

import org.json.JSONException;
import org.json.JSONObject;

public class ArtAppsMaxAdapter extends MediationAdapterBase implements MaxInterstitialAdapter {
    private static final String TAG = "ArtAppsMaxAdapter";

    private ArtAppsInterstitial interstitialAd;

    public ArtAppsMaxAdapter(AppLovinSdk sdk) {
        super(sdk);
    }

    private Bundle getCustomParameters(Object parameters) {
        try {
            Object value = parameters.getClass().getMethod("getCustomParameters").invoke(parameters);
            if (value instanceof Bundle) {
                return (Bundle) value;
            }
        } catch (Throwable ignored) {
            // Older MAX SDK versions do not expose custom parameters on every parameter type.
        }
        return new Bundle();
    }

    private String resolvedParameter(
            String key,
            Bundle customParameters,
            Bundle serverParameters
    ) {
        String directValue = stringValue(customParameters.get(key));
        if (directValue != null) {
            return directValue;
        }

        Object nested = customParameters.get("custom_parameters");
        if (nested instanceof Bundle) {
            String nestedValue = stringValue(((Bundle) nested).get(key));
            if (nestedValue != null) {
                return nestedValue;
            }
        } else if (nested instanceof String) {
            try {
                JSONObject json = new JSONObject((String) nested);
                String nestedValue = stringValue(json.opt(key));
                if (nestedValue != null) {
                    return nestedValue;
                }
            } catch (JSONException ignored) {
                // Fall through to MAX server parameters.
            }
        }

        return stringValue(serverParameters.get(key));
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        String result;
        if (value instanceof String) {
            result = (String) value;
        } else if (value instanceof Number || value instanceof Boolean) {
            result = String.valueOf(value);
        } else {
            return null;
        }

        result = result.trim();
        return result.isEmpty() ? null : result;
    }

    @Override
    public void initialize(
            MaxAdapterInitializationParameters parameters,
            Activity activity,
            MaxAdapter.OnCompletionListener onCompletionListener
    ) {
        ArtAppsReachability.startMonitoring(getApplicationContext());
        Bundle customParameters = getCustomParameters(parameters);
        Bundle serverParameters = parameters.getServerParameters();
        String partnerId = resolvedParameter("partner_id", customParameters, serverParameters);
        String appId = resolvedParameter("app_id", customParameters, serverParameters);
        ArtApps.getInstance().initialize(
                partnerId != null ? partnerId : "test_partner",
                appId != null ? appId : "test_app",
                null
        );
        onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.DOES_NOT_APPLY, null);
    }

    @Override
    public String getSdkVersion() {
        return "1.0.6";
    }

    @Override
    public String getAdapterVersion() {
        return "1.0.6.0";
    }

    @Override
    public void onDestroy() {
        if (interstitialAd != null && ArtAppsInterstitial.currentAd != interstitialAd) {
            interstitialAd.setDelegate(null);
        }
        interstitialAd = null;
    }

    private void clearInterstitialAd(ArtAppsInterstitial ad) {
        if (interstitialAd == ad) {
            interstitialAd.setDelegate(null);
            interstitialAd = null;
        }
    }

    private void bindInterstitialDelegate(
            ArtAppsInterstitial ad,
            MaxInterstitialAdapterListener listener,
            InterstitialPhase phase
    ) {
        ad.setDelegate(new InterstitialAdapterDelegate(this, listener, phase));
    }

    @Override
    public void loadInterstitialAd(
            MaxAdapterResponseParameters parameters,
            Activity activity,
            MaxInterstitialAdapterListener listener
    ) {
        ArtAppsReachability.startMonitoring(getApplicationContext());
        String placementId = parameters.getThirdPartyAdPlacementId();
        Bundle customParameters = getCustomParameters(parameters);
        Bundle serverParameters = parameters.getServerParameters();
        String partnerId = resolvedParameter("partner_id", customParameters, serverParameters);
        String appId = resolvedParameter("app_id", customParameters, serverParameters);
        ArtApps.getInstance().initialize(
                partnerId != null ? partnerId : "test_partner",
                appId != null ? appId : "test_app",
                null
        );

        interstitialAd = new ArtAppsInterstitial(placementId);
        bindInterstitialDelegate(interstitialAd, listener, InterstitialPhase.LOAD);
        interstitialAd.load(getApplicationContext());
    }

    @Override
    public void showInterstitialAd(
            MaxAdapterResponseParameters parameters,
            Activity activity,
            MaxInterstitialAdapterListener listener
    ) {
        if (interstitialAd == null || !interstitialAd.isReady()) {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
            if (interstitialAd != null) {
                interstitialAd.setDelegate(null);
                interstitialAd = null;
            }
            return;
        }

        bindInterstitialDelegate(interstitialAd, listener, InterstitialPhase.DISPLAY);
        interstitialAd.show(activity);
    }

    private enum InterstitialPhase {
        LOAD,
        DISPLAY
    }

    private enum InterstitialState {
        PENDING,
        LOADED,
        DISPLAYED,
        COMPLETED
    }

    private static final class InterstitialAdapterDelegate
            implements ArtAppsInterstitial.InterstitialDelegate {
        private final ArtAppsMaxAdapter parentAdapter;
        private final MaxInterstitialAdapterListener maxListener;
        private final InterstitialPhase phase;
        private InterstitialState state = InterstitialState.PENDING;

        InterstitialAdapterDelegate(
                ArtAppsMaxAdapter parentAdapter,
                MaxInterstitialAdapterListener maxListener,
                InterstitialPhase phase
        ) {
            this.parentAdapter = parentAdapter;
            this.maxListener = maxListener;
            this.phase = phase;
        }

        @Override
        public void onAdLoaded(ArtAppsInterstitial ad) {
            if (phase != InterstitialPhase.LOAD || state != InterstitialState.PENDING) {
                logIgnoredCallback("didLoad");
                return;
            }

            state = InterstitialState.LOADED;
            Log.d(TAG, "State: loaded");
            maxListener.onInterstitialAdLoaded();
        }

        @Override
        public void onAdFailedToLoad(ArtAppsInterstitial ad, int errorCode, String message) {
            if (phase != InterstitialPhase.LOAD || state != InterstitialState.PENDING) {
                logIgnoredCallback("didFailToLoad");
                return;
            }

            state = InterstitialState.COMPLETED;
            Log.d(TAG, "State: load-failed (" + message + ")");
            maxListener.onInterstitialAdLoadFailed(new MaxAdapterError(errorCode, message));
            parentAdapter.clearInterstitialAd(ad);
        }

        @Override
        public void onAdShown(ArtAppsInterstitial ad) {
            if (phase != InterstitialPhase.DISPLAY || state != InterstitialState.PENDING) {
                logIgnoredCallback("didDisplay");
                return;
            }

            state = InterstitialState.DISPLAYED;
            Log.d(TAG, "State: displayed");
            maxListener.onInterstitialAdDisplayed();
        }

        @Override
        public void onAdHidden(ArtAppsInterstitial ad) {
            if (phase != InterstitialPhase.DISPLAY) {
                logIgnoredCallback("didHide");
                return;
            }

            if (state == InterstitialState.DISPLAYED) {
                state = InterstitialState.COMPLETED;
                Log.d(TAG, "State: hidden");
                maxListener.onInterstitialAdHidden();
                parentAdapter.clearInterstitialAd(ad);
            } else if (state == InterstitialState.PENDING) {
                state = InterstitialState.COMPLETED;
                Log.d(TAG, "Received didHide before didDisplay; reporting display failure");
                maxListener.onInterstitialAdDisplayFailed(
                        new MaxAdapterError(307, "Ad hidden before display")
                );
                parentAdapter.clearInterstitialAd(ad);
            } else {
                logIgnoredCallback("didHide");
            }
        }

        @Override
        public void onAdClicked(ArtAppsInterstitial ad) {
            if (phase != InterstitialPhase.DISPLAY || state != InterstitialState.DISPLAYED) {
                logIgnoredCallback("didClick");
                return;
            }
            maxListener.onInterstitialAdClicked();
        }

        @Override
        public void onAdFailedToShow(ArtAppsInterstitial ad, int errorCode, String message) {
            if (phase != InterstitialPhase.DISPLAY) {
                logIgnoredCallback("didFailToDisplay");
                return;
            }

            if (state == InterstitialState.PENDING) {
                state = InterstitialState.COMPLETED;
                Log.d(TAG, "State: display-failed (" + message + ")");
                maxListener.onInterstitialAdDisplayFailed(new MaxAdapterError(errorCode, message));
                parentAdapter.clearInterstitialAd(ad);
            } else if (state == InterstitialState.DISPLAYED) {
                state = InterstitialState.COMPLETED;
                Log.d(TAG, "Normalized failure-after-display to didHide");
                maxListener.onInterstitialAdHidden();
                parentAdapter.clearInterstitialAd(ad);
            } else {
                logIgnoredCallback("didFailToDisplay");
            }
        }

        private void logIgnoredCallback(String callback) {
            Log.d(TAG, "Ignored " + callback + " in phase=" + phase + ", state=" + state);
        }
    }
}
