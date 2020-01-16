package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.google.ads.interactivemedia.v3.api.CompanionAd;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;

public class InnovidAdWrapper {
    private static final String DEVICE_TYPE = "firetv";
    private static final String CAT = "InnovidAdWrapper";
    private static final String CAT_JS = "InnovidAdWrapper / JS";
    private static final String JS_INTERFACE_OBJECT_NAME = "__iface";
    private static final HashMap<String, InnovidAdEventType> adEventTypeByString = createAdEventTypeByAliasMap();

    private static final String MSG_TYPE_SSAI_PLAYBACK_UPDATE = "playback-update";
    private static final String MSG_TYPE_REQUEST_STOP_IROLL = "iroll-request-stop";
    private static final String MSG_TEMPLATE_JS = "javascript:(function() { window.postMessage(%s, '*'); })();";

    private WebView mWebView;
    private CompanionAd mAdInfo;

    private Boolean mStarted;
    private Boolean mStopRequested;
    private String mAdvertisingId;

    private InnovidAdEventListener mAdEventListener;
    private InnovidAdPlaybackRequestListener mAdPlaybackRequestListener;
    private InnovidAdPlaybackProgressListener mAdPlaybackProgressListener;

    public InnovidAdWrapper(WebView webView, CompanionAd adInfo, String advertisingId) {
        mWebView = webView;
        mAdInfo = adInfo;

        mStarted = false;
        mStopRequested = false;
        mAdvertisingId = advertisingId;
    }

    public void start() {
        if (mStarted) {
            return;
        }

        mStarted = true;

        configureWebView();
        configureWebViewJSInterface();

        String iAdUrl = generateIAdUrl();

        Log.d(CAT, String.format("start(url: %s)", iAdUrl));

        mWebView.loadUrl(iAdUrl);
        mWebView.requestFocus();
    }

    public void requestStop() {
        Log.d(CAT, "requestStop()");

        if (!mStarted || mStopRequested) {
            return;
        }

        // give a time to iroll to complete all calls and dispose itself
        postMessageToAd(MSG_TYPE_REQUEST_STOP_IROLL, null);

        // TODO: create timeout (1-2s) and wait for "iroll-ended" event
        // and then cleanup refs

        mStopRequested = true;
    }

    public void enforceStop() {
        Log.d(CAT, "enforceStop()");

        if (!mStarted) {
            return;
        }

        mWebView.clearHistory();
        mWebView.loadUrl("about:blank");

        mStarted = false;
    }

    public void setAdEventListener(InnovidAdEventListener listener) {
        mAdEventListener = listener;
    }

    public void setAdPlaybackRequestListener(InnovidAdPlaybackRequestListener listener) {
        mAdPlaybackRequestListener = listener;
    }

    public void setAdPlaybackProgressListener(InnovidAdPlaybackProgressListener listener) {
        mAdPlaybackProgressListener = listener;
    }

    public void injectPlaybackProgressInfo(SSAIPlaybackState playbackState, double position, double duration) {
        JSONObject data = new JSONObject();

        try {
            data.put("startAt", 0);
            data.put("playbackState", playbackState.alias);
            data.put("position", position);
            data.put("duration", duration);
        } catch(Exception ignored) {}

        postMessageToAd(MSG_TYPE_SSAI_PLAYBACK_UPDATE, data);
    }


    private void postMessageToAd(String type, JSONObject data) {
        JSONObject message = new JSONObject();

        try {
            message.put("type", type);
            message.put("data", data);
        } catch (JSONException ignored) {}

        mWebView.loadUrl(String.format(MSG_TEMPLATE_JS, message.toString()));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setUseWideViewPort(true);
        mWebView.getSettings().setDisplayZoomControls(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
            mWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }

        mWebView.setBackgroundColor(0x00000000);
        mWebView.setVisibility(View.VISIBLE);
        mWebView.setFocusable(true);
        mWebView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                Log.d(CAT, String.format("onKey(key: %s, action: %s)", i, keyEvent.getAction()));
                // Check if the key event was the Back button and if there's history
                if (i == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_DOWN && mWebView.canGoBack()) {
                    mWebView.goBack();
                    return true;
                }

                return false;
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(CAT_JS, cm.message());
                return true;
            }
        });
    }

    private String generateIAdUrl() {
        String encodedParams = "";

//        final String tagUrl = mAdInfo.getResourceValue();
        final String tagUrl = "http://tv.innovid.com/tag/get.php?tag=1maft2&kind=placement&device=ctv-html";

        try {
            encodedParams = URLEncoder.encode(getIAdStartupParametersAsString(), "UTF-8");
        } catch (UnsupportedEncodingException ignored) {}

        return String.format("%s#params=%s", tagUrl, encodedParams);
    }

    @SuppressLint("addJavascriptInterface")
    private void configureWebViewJSInterface() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mWebView.addJavascriptInterface(new InnovidAdWrapperJSInterface(), JS_INTERFACE_OBJECT_NAME);
        }
    }

    private class InnovidAdWrapperJSInterface {
        private static final String REQUEST_RESUME = "iroll-request-playback-resume";
        private static final String REQUEST_PAUSE = "iroll-request-playback-pause";
        private static final String REQUEST_STOP_AND_RESTART_ON_NEXT_RESUME = "iroll-request-playback-restart-on-resume";

        @JavascriptInterface
        public void processAdPlaybackRequest(String type) {
            Log.d(CAT, String.format("processPlaybackRequest(%s)", type));

            if (mAdPlaybackRequestListener == null) {
                return;
            }

            if (REQUEST_RESUME.equals( type )) {
                mAdPlaybackRequestListener.onResumeRequest();
            } else if (REQUEST_PAUSE.equals( type )) {
                mAdPlaybackRequestListener.onPauseRequest();
            } else if (REQUEST_STOP_AND_RESTART_ON_NEXT_RESUME.equals( type )) {
                mAdPlaybackRequestListener.onStopAndRestartOnNextResumeRequest();
            }
        }

        @JavascriptInterface
        public void processAdPlaybackProgress(double position, double duration) {
            if (mAdPlaybackProgressListener != null) {
                mAdPlaybackProgressListener.onProgress( position, duration );
            }
        }

        @JavascriptInterface
        public void processAdEvent(String type) {
            if (mAdEventListener != null) {
                mAdEventListener.onInnovidAdEvent(adEventTypeByString.get( type ));
            }
        }
    }

    private String getIAdStartupParametersAsString() {
        JSONObject json = new JSONObject();
        JSONObject keymap = new JSONObject();
        JSONObject iface = new JSONObject();

        try {
            keymap.put("UP", 38);
            keymap.put("LEFT", 37);
            keymap.put("RIGHT", 39);
            keymap.put("DOWN", 40);
            keymap.put("ENTER", 13);
            keymap.put("BACK", 8);
            keymap.put("PLAY_PAUSE", 179);

            iface.put("type", "android-native-wrapper");
            iface.put("property", JS_INTERFACE_OBJECT_NAME);

            json.put("platform", DEVICE_TYPE);
            json.put("advertisingId", mAdvertisingId);
            json.put("keyMap", keymap);
            json.put("ssai", true);
            json.put("iface", iface);

        } catch (JSONException ignored) {}

        return json.toString();
    }

    private static HashMap<String, InnovidAdEventType> createAdEventTypeByAliasMap() {
        HashMap<String, InnovidAdEventType> result = new HashMap<>();

        result.put("iroll-ready", InnovidAdEventType.READY);
        result.put("iroll-started", InnovidAdEventType.STARTED);
        result.put("iroll-impression", InnovidAdEventType.IMPRESSION);
        result.put("iroll-engage", InnovidAdEventType.ENGAGE);
        result.put("iroll-expand", InnovidAdEventType.EXPAND);
        result.put("iroll-collapse", InnovidAdEventType.COLLAPSE);
        result.put("iroll-ended", InnovidAdEventType.ENDED);
        result.put("iroll-failed", InnovidAdEventType.FAILED);
        result.put("iroll-video-started", InnovidAdEventType.VIDEO_STARTED);
        result.put("iroll-video-first-quartile", InnovidAdEventType.VIDEO_FIRST_QUARTILE);
        result.put("iroll-video-midpoint", InnovidAdEventType.VIDEO_MIDPOINT);
        result.put("iroll-video-third-quartile", InnovidAdEventType.VIDEO_THIRD_QUARTILE);
        result.put("iroll-video-completed", InnovidAdEventType.VIDEO_COMPLETED);
        result.put("iroll-back-pressed", InnovidAdEventType.BACK_PRESSED);

        return result;
    }

    public interface InnovidAdPlaybackRequestListener {
        void onPauseRequest();
        void onResumeRequest();
        void onStopAndRestartOnNextResumeRequest();
    }

    public interface InnovidAdPlaybackProgressListener {
        void onProgress(double position, double duration);
    }

    public interface InnovidAdEventListener {
        void onInnovidAdEvent(InnovidAdEventType type);
    }

    public enum InnovidAdEventType {
        READY,
        STARTED,
        IMPRESSION,
        ENGAGE,
        EXPAND,
        COLLAPSE,
        ENDED,
        FAILED,
        VIDEO_STARTED,
        VIDEO_FIRST_QUARTILE,
        VIDEO_MIDPOINT,
        VIDEO_THIRD_QUARTILE,
        VIDEO_COMPLETED,
        BACK_PRESSED
    }

    public enum SSAIPlaybackState {
        PLAYING("playing"),
        PAUSED("paused");

        public final String alias;

        SSAIPlaybackState(String alias) {
            this.alias = alias;
        }
    }
}
