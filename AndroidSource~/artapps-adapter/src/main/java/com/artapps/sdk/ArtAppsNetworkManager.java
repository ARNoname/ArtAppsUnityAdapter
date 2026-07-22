package com.artapps.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArtAppsNetworkManager {
    private static final String TAG = "ArtAppsNetwork";
    private static final String PREFS_NAME = "ArtAppsPrefs";
    private static final String KEY_REQUEST_ID = "ArtApps_request_id";
    private static final int REQUEST_TIMEOUT_MS = 5000;

    private static ArtAppsNetworkManager instance;

    private String baseURL = "https://api.adw.net/applovin/request";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized ArtAppsNetworkManager getInstance() {
        if (instance == null) {
            instance = new ArtAppsNetworkManager();
        }
        return instance;
    }

    private ArtAppsNetworkManager() {
    }

    public void setBaseURL(String url) {
        baseURL = url;
    }

    public void fetchAd(
            final Context context,
            final String partnerId,
            final String appId,
            final String placementId,
            final AdResponseCallback callback
    ) {
        if (!ArtAppsReachability.isConnectedToNetwork(context)) {
            postError(callback, new IOException("No internet connection"));
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    String requestId = persistentRequestID(context);
                    StringBuilder urlBuilder = new StringBuilder(baseURL);
                    urlBuilder.append("?request_id=")
                            .append(URLEncoder.encode(requestId, "UTF-8"));
                    urlBuilder.append("&partner_id=")
                            .append(URLEncoder.encode(partnerId, "UTF-8"));
                    urlBuilder.append("&app_id=")
                            .append(URLEncoder.encode(appId, "UTF-8"));
                    urlBuilder.append("&placement=")
                            .append(URLEncoder.encode(placementId, "UTF-8"));

                    connection = (HttpURLConnection) new URL(urlBuilder.toString()).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
                    connection.setReadTimeout(REQUEST_TIMEOUT_MS);

                    int responseCode = connection.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP Error: " + responseCode);
                    }

                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()))) {
                        String inputLine;
                        while ((inputLine = reader.readLine()) != null) {
                            response.append(inputLine);
                        }
                    }

                    final JSONObject jsonResponse = new JSONObject(response.toString());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(jsonResponse);
                        }
                    });
                } catch (Exception error) {
                    Log.e(TAG, "Fetch ad failed", error);
                    postError(callback, error);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    private String persistentRequestID(Context context) {
        if (context == null) {
            return UUID.randomUUID().toString();
        }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String requestId = prefs.getString(KEY_REQUEST_ID, null);
        if (requestId != null && !requestId.isEmpty()) {
            return requestId;
        }

        requestId = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_REQUEST_ID, requestId).apply();
        return requestId;
    }

    public void trackImpression(
            final String requestId,
            final String trackUrl,
            final int visibleSeconds
    ) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    String urlString = trackUrl;
                    if (urlString == null || urlString.isEmpty()) {
                        urlString = "https://api.adw.net/applovin/track?request_id="
                                + URLEncoder.encode(requestId, "UTF-8")
                                + "&event=impression";
                    }

                    urlString += urlString.contains("?")
                            ? "&was_visible=" + visibleSeconds
                            : "?was_visible=" + visibleSeconds;

                    connection = (HttpURLConnection) new URL(urlString).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
                    connection.setReadTimeout(REQUEST_TIMEOUT_MS);
                    int code = connection.getResponseCode();
                    Log.d(TAG, "Track impression response: " + code);
                } catch (Exception error) {
                    Log.e(TAG, "Track impression failed", error);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    private void postError(final AdResponseCallback callback, final Exception error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    public interface AdResponseCallback {
        void onSuccess(JSONObject response);

        void onError(Exception error);
    }
}
