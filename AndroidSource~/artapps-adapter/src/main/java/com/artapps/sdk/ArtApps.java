package com.artapps.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Date;

public class ArtApps {
    private static final String TAG = "ArtApps";
    private static final String PREFS_NAME = "ArtAppsPrefs";
    private static final String KEY_LAST_SHOW_TIME = "ArtApps_lastShowTime";

    private static ArtApps instance;

    private String partnerId;
    private String appId;
    private boolean isInitialized;
    private Integer serverCooldownSeconds;
    private Integer serverTtlSeconds;
    private Date serverRestrictionsUpdatedAt;

    public long frequencyCapSeconds = 90L;

    public static synchronized ArtApps getInstance() {
        if (instance == null) {
            instance = new ArtApps();
        }
        return instance;
    }

    private ArtApps() {
    }

    public void initialize(String partnerId, String appId, String baseURL) {
        this.partnerId = partnerId;
        this.appId = appId;
        if (baseURL != null && !baseURL.isEmpty()) {
            ArtAppsNetworkManager.getInstance().setBaseURL(baseURL);
        }

        isInitialized = true;
        Log.d(TAG, "Initialized SDK 1.0.6. PartnerID: " + partnerId + ", AppID: " + appId);
    }

    public String getPartnerId() {
        return partnerId;
    }

    public String getAppId() {
        return appId;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean canShowAd(Context context) {
        long now = System.currentTimeMillis();
        long lastShowTime = getLastShowTime(context);
        long effectiveCooldown = getEffectiveCooldown(now);
        if (lastShowTime > 0L) {
            long elapsedSeconds = (now - lastShowTime) / 1000L;
            if (elapsedSeconds < effectiveCooldown) {
                String source = serverCooldownSeconds == null ? "Freq Cap" : "Server Cooldown";
                Log.d(TAG, "Blocked by " + source + " (need " + effectiveCooldown
                        + "s, passed " + elapsedSeconds + "s)");
                return false;
            }
        }
        return true;
    }

    public void didShowAd(Context context) {
        setLastShowTime(context, System.currentTimeMillis());
    }

    public void updateServerRestrictions(
            Integer cooldownSeconds,
            Integer sessionGateSeconds,
            Integer ttlSeconds
    ) {
        serverRestrictionsUpdatedAt = new Date();
        serverCooldownSeconds = cooldownSeconds;
        serverTtlSeconds = ttlSeconds;
    }

    private long getEffectiveCooldown(long nowMillis) {
        checkTtl(new Date(nowMillis));
        return serverCooldownSeconds != null ? serverCooldownSeconds : frequencyCapSeconds;
    }

    private void checkTtl(Date now) {
        if (serverRestrictionsUpdatedAt == null || serverTtlSeconds == null || serverTtlSeconds <= 0) {
            return;
        }

        long elapsed = (now.getTime() - serverRestrictionsUpdatedAt.getTime()) / 1000L;
        if (elapsed > serverTtlSeconds) {
            Log.d(TAG, "Server restrictions expired (TTL: " + serverTtlSeconds + "s)");
            serverCooldownSeconds = null;
            serverTtlSeconds = null;
            serverRestrictionsUpdatedAt = null;
        }
    }

    private long getLastShowTime(Context context) {
        if (context == null) {
            return 0L;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_SHOW_TIME, 0L);
    }

    private void setLastShowTime(Context context, long time) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_SHOW_TIME, time).apply();
    }
}
