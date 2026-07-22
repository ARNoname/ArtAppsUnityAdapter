package com.artapps.sdk;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

final class ArtAppsReachability {
    private static final String TAG = "ArtAppsReachability";
    private static final Object LOCK = new Object();

    private static volatile boolean hasNetworkState;
    private static volatile boolean connected;
    private static boolean monitorStarted;
    private static ConnectivityManager connectivityManager;

    private ArtAppsReachability() {
    }

    static void startMonitoring(Context context) {
        if (context == null) {
            return;
        }

        synchronized (LOCK) {
            if (connectivityManager == null) {
                Context applicationContext = context.getApplicationContext();
                connectivityManager = (ConnectivityManager) applicationContext
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
            }

            refreshFromSystem();
            if (monitorStarted || connectivityManager == null) {
                return;
            }

            monitorStarted = true;
            try {
                ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        refreshFromSystem();
                    }

                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                        refreshFromSystem();
                    }

                    @Override
                    public void onLost(Network network) {
                        refreshFromSystem();
                    }

                    @Override
                    public void onUnavailable() {
                        updateState(false);
                    }
                };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    connectivityManager.registerDefaultNetworkCallback(callback);
                } else {
                    NetworkRequest request = new NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build();
                    connectivityManager.registerNetworkCallback(request, callback);
                }
                Log.d(TAG, "Network monitor active. State: " + statusDescription());
            } catch (RuntimeException error) {
                Log.w(TAG, "Network monitor unavailable; using synchronous checks", error);
            }
        }
    }

    static boolean isConnectedToNetwork(Context context) {
        startMonitoring(context);
        refreshFromSystem();
        return hasNetworkState && connected;
    }

    static String statusDescription() {
        if (!hasNetworkState) {
            return "waiting-for-initial-network";
        }
        return connected ? "validated" : "unavailable";
    }

    private static void refreshFromSystem() {
        ConnectivityManager manager = connectivityManager;
        if (manager == null) {
            updateState(false);
            return;
        }

        try {
            updateState(queryConnected(manager));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to read network state", error);
            updateState(false);
        }
    }

    private static boolean queryConnected(ConnectivityManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = manager.getActiveNetwork();
            if (activeNetwork == null) {
                return false;
            }

            NetworkCapabilities capabilities = manager.getNetworkCapabilities(activeNetwork);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        return legacyConnected(manager);
    }

    @SuppressWarnings("deprecation")
    private static boolean legacyConnected(ConnectivityManager manager) {
        NetworkInfo activeNetwork = manager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private static void updateState(boolean newValue) {
        boolean didChange = !hasNetworkState || connected != newValue;
        hasNetworkState = true;
        connected = newValue;
        if (didChange) {
            Log.d(TAG, "Network state changed: " + statusDescription());
        }
    }
}
