package com.artapps.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.artapps.sdk.plugin.R;

public class ArtAppsWebViewActivity extends Activity {
    private static final String TAG = "ArtAppsWebView";
    private static final int LOAD_TIMEOUT_MS = 5000;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable loadWatchdog = new Runnable() {
        @Override
        public void run() {
            if (!didFinishInitialNavigation
                    && !didHandleWebViewFailure
                    && !isFinishing()) {
                handleWebViewFailure("Ad web view load timed out");
            }
        }
    };

    private WebView webView;
    private Button closeButton;
    private TextView timerText;
    private String adUrl;
    private long displayStartTime;
    private int durationSeconds = 20;
    private boolean didStartInitialNavigation;
    private boolean didNotifyDisplay;
    private boolean didHandleWebViewFailure;
    private boolean didFinishInitialNavigation;
    private boolean didStartTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adUrl = getIntent().getStringExtra("url");
        durationSeconds = Math.max(1, getIntent().getIntExtra("duration", 20));
        if (adUrl == null || adUrl.trim().isEmpty()) {
            notifyDisplayFailedAndFinish(302, "Missing ad URL");
            return;
        }

        adUrl = adUrl.trim();
        setupContentView();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        startInitialNavigationIfNeeded();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupContentView() {
        RelativeLayout container = new RelativeLayout(this);
        container.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(createWebViewClient());
        container.addView(
                webView,
                new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT
                )
        );

        closeButton = new Button(this);
        closeButton.setText(R.string.artapps_close);
        closeButton.setVisibility(View.GONE);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        container.addView(closeButton, topEndLayoutParams());

        timerText = new TextView(this);
        timerText.setTextColor(Color.WHITE);
        timerText.setBackgroundColor(Color.parseColor("#80000000"));
        timerText.setPadding(20, 10, 20, 10);
        timerText.setText(getString(R.string.artapps_countdown, durationSeconds));
        timerText.setVisibility(View.GONE);
        container.addView(timerText, topEndLayoutParams());

        setContentView(container);
    }

    private RelativeLayout.LayoutParams topEndLayoutParams() {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.setMargins(20, 20, 20, 20);
        return params;
    }

    private void startInitialNavigationIfNeeded() {
        if (didStartInitialNavigation || didHandleWebViewFailure || isFinishing()) {
            return;
        }

        ArtAppsReachability.startMonitoring(getApplicationContext());
        Log.d(TAG, "Fullscreen activity resumed. Network: "
                + ArtAppsReachability.statusDescription());
        if (!ArtAppsReachability.isConnectedToNetwork(this)) {
            notifyDisplayFailedAndFinish(305, "No internet connection");
            return;
        }

        didStartInitialNavigation = true;
        webView.loadUrl(adUrl);
        timerHandler.postDelayed(loadWatchdog, LOAD_TIMEOUT_MS);
    }

    private WebViewClient createWebViewClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new ArtAppsWebViewClientApi26();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new ArtAppsWebViewClientApi23();
        }
        return new ArtAppsWebViewClient();
    }

    private class ArtAppsWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            didFinishInitialNavigation = true;
            notifyDisplayIfNeeded();
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(
                WebView view,
                int errorCode,
                String description,
                String failingUrl
        ) {
            if (!didFinishInitialNavigation) {
                handleWebViewFailure("WebView failed: " + description);
            }
        }
    }

    @SuppressLint("NewApi")
    private class ArtAppsWebViewClientApi23 extends ArtAppsWebViewClient {
        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request != null && request.isForMainFrame()) {
                String description = error != null
                        ? String.valueOf(error.getDescription())
                        : "Unknown WebView error";
                handleWebViewFailure("WebView failed: " + description);
            }
        }

        @Override
        public void onReceivedHttpError(
                WebView view,
                WebResourceRequest request,
                WebResourceResponse errorResponse
        ) {
            if (request != null
                    && request.isForMainFrame()
                    && errorResponse != null
                    && errorResponse.getStatusCode() >= 400) {
                handleWebViewFailure(
                        "WebView HTTP error: " + errorResponse.getStatusCode()
                );
            }
        }
    }

    @SuppressLint("NewApi")
    private final class ArtAppsWebViewClientApi26 extends ArtAppsWebViewClientApi23 {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            handleWebViewFailure("Ad web content process terminated");
            return true;
        }
    }

    private void notifyDisplayIfNeeded() {
        if (didNotifyDisplay
                || didHandleWebViewFailure
                || ArtAppsInterstitial.currentAd == null) {
            return;
        }

        didNotifyDisplay = true;
        displayStartTime = System.currentTimeMillis();
        timerHandler.removeCallbacks(loadWatchdog);
        startTimer();
        ArtAppsInterstitial.currentAd.notifyShown(this);
    }

    private void startTimer() {
        if (didStartTimer) {
            return;
        }
        didStartTimer = true;
        timerText.setVisibility(View.VISIBLE);
        timerHandler.postDelayed(new Runnable() {
            private int remaining = durationSeconds;

            @Override
            public void run() {
                remaining--;
                if (remaining > 0) {
                    timerText.setText(getString(R.string.artapps_countdown, remaining));
                    timerHandler.postDelayed(this, 1000L);
                } else {
                    timerText.setVisibility(View.GONE);
                    closeButton.setVisibility(View.VISIBLE);
                }
            }
        }, 1000L);
    }

    private void handleWebViewFailure(String message) {
        if (didHandleWebViewFailure || isFinishing()) {
            return;
        }

        didHandleWebViewFailure = true;
        timerHandler.removeCallbacks(loadWatchdog);
        Log.d(TAG, "WebView failure. Displayed: " + didNotifyDisplay + ". " + message);
        if (!didNotifyDisplay) {
            notifyDisplayFailedAndFinish(306, message);
        } else {
            finish();
        }
    }

    private void notifyDisplayFailedAndFinish(int errorCode, String message) {
        didHandleWebViewFailure = true;
        timerHandler.removeCallbacks(loadWatchdog);
        if (ArtAppsInterstitial.currentAd != null) {
            ArtAppsInterstitial.currentAd.notifyFailedToShow(errorCode, message);
        }
        finish();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (closeButton != null && closeButton.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacksAndMessages(null);
        ArtAppsInterstitial ad = ArtAppsInterstitial.currentAd;

        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }

        if (ad != null) {
            int visibleSeconds = displayStartTime > 0L
                    ? (int) ((System.currentTimeMillis() - displayStartTime) / 1000L)
                    : 0;
            String requestId = getIntent().getStringExtra("requestId");
            String trackUrl = getIntent().getStringExtra("trackUrl");
            if (didNotifyDisplay
                    && !didHandleWebViewFailure
                    && requestId != null) {
                ArtAppsNetworkManager.getInstance()
                        .trackImpression(requestId, trackUrl, visibleSeconds);
            }

            if (didNotifyDisplay) {
                ad.notifyHidden();
            } else if (!didHandleWebViewFailure) {
                ad.notifyFailedToShow(307, "Ad activity closed before display");
            }
        }

        super.onDestroy();
    }
}
