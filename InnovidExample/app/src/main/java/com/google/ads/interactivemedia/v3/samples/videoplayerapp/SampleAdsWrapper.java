/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdProgressInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.CompanionAd;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer.SampleVideoPlayerCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class adds ad-serving support to Sample HlsVideoPlayer
 */
public class SampleAdsWrapper implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener,
        AdsLoader.AdsLoadedListener {

    // Live stream asset key.
    private static final String TEST_ASSET_KEY = "sN_IYUG8STe1ZzhIIE_ksA";

    // VOD HLS content source and video IDs.
    private static final String TEST_HLS_CONTENT_SOURCE_ID = "2490667";
    private static final String TEST_HLS_VIDEO_ID = "googleio-highlights";

    // VOD DASH content source and video IDs.
    private static final String TEST_DASH_CONTENT_SOURCE_ID = "2474148";
    private static final String TEST_DASH_VIDEO_ID = "bbb-clear";

    private static final String PLAYER_TYPE = "DAISamplePlayer";

    private enum ContentType {
        LIVE_HLS,
        VOD_HLS,
        VOD_DASH,
    }

    // Select a LIVE HLS stream. To play a VOD HLS stream or a VOD DASH stream, set CONTENT_TYPE to
    // the associated enum.
    private static final ContentType CONTENT_TYPE = ContentType.VOD_HLS;

    /**
     * Log interface, so we can output the log commands to the UI or similar.
     */
    public interface Logger {
        void log(String logMessage);
    }

    private ImaSdkFactory sdkFactory;
    private AdsLoader adsLoader;
    private StreamDisplayContainer streamDisplayContainer;
    private StreamManager streamManager;
    private List<VideoStreamPlayer.VideoStreamPlayerCallback> videoPlayerCallbacks;


    private String fallbackUrl;
    private Logger logger;
    private InnovidAdWrapper innovidAdWrapper;
    private String advertisingId;

    private final Context context;
    private final SampleVideoPlayer videoPlayer;
    private final ViewGroup videoPlayerContainer;
    private final ViewGroup adUiContainer;
    private final WebView webView;


    /**
     * Creates a new SampleAdsWrapper that implements IMA direct-ad-insertion.
     *
     * @param context       the app's context.
     * @param videoPlayer   underlying HLS video player.
     * @param videoPlayerContainer ViewGroup containing the video player view
     * @param adUiContainer ViewGroup in which to display the ad's UI.
     * @param webView WebView in which the ad is displayed.
     */
    public SampleAdsWrapper(
            Context context,
            SampleVideoPlayer videoPlayer,
            ViewGroup videoPlayerContainer,
            ViewGroup adUiContainer,
            WebView webView
    ) {
        this.context = context;
        this.videoPlayer = videoPlayer;
        this.videoPlayerContainer = videoPlayerContainer;
        this.adUiContainer = adUiContainer;
        this.webView = webView;
        sdkFactory = ImaSdkFactory.getInstance();
        videoPlayerCallbacks = new ArrayList<>();
        createAdsLoader();
    }

    public void releaseInteractiveAd() {
        disposeCurrentInteractiveAd();
    }

    @TargetApi(19)
    private void enableWebViewDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void createAdsLoader() {
        ImaSdkSettings settings = ImaSdkFactory.getInstance().createImaSdkSettings();
        // Change any settings as necessary here.
        settings.setPlayerType(PLAYER_TYPE);
        enableWebViewDebugging();
        streamDisplayContainer = sdkFactory.createStreamDisplayContainer();
        VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
        videoPlayer.setSampleVideoPlayerCallback(
                new SampleVideoPlayerCallback() {
                    @Override
                    public void onUserTextReceived(String userText) {
                        for (VideoStreamPlayer.VideoStreamPlayerCallback callback :
                                videoPlayerCallbacks) {
                            callback.onUserTextReceived(userText);
                        }
                    }

                    @Override
                    public void onSeek(int windowIndex, long positionMs) {
                        // See if we would seek past an ad, and if so, jump back to it.
                        long newSeekPositionMs = positionMs;
                        if (streamManager != null) {
                            CuePoint prevCuePoint = streamManager.getPreviousCuePointForStreamTime(
                                    positionMs / 1000.0);
                            if (prevCuePoint != null && !prevCuePoint.isPlayed()) {
                                newSeekPositionMs = (long) (prevCuePoint.getStartTime() * 1000);
                            }
                        }
                        videoPlayer.seekTo(windowIndex, newSeekPositionMs);
                    }
                });
        streamDisplayContainer.setVideoStreamPlayer(videoStreamPlayer);
        streamDisplayContainer.setAdContainer(webView);
        adsLoader = sdkFactory.createAdsLoader(context, settings, streamDisplayContainer);
    }

    public void requestAndPlayAds() {
        adsLoader.addAdErrorListener(this);
        adsLoader.addAdsLoadedListener(this);
        adsLoader.requestStream(buildStreamRequest());
    }

    private StreamRequest buildStreamRequest() {
        StreamRequest request;
        switch (CONTENT_TYPE) {
            case LIVE_HLS:
                // Live HLS stream request.
                return sdkFactory.createLiveStreamRequest(TEST_ASSET_KEY, null);
            case VOD_HLS:
                // VOD HLS request.
                request = sdkFactory.createVodStreamRequest(
                        TEST_HLS_CONTENT_SOURCE_ID,
                        TEST_HLS_VIDEO_ID,
                        null); // apiKey
                request.setFormat(StreamFormat.HLS);
                return request;
            case VOD_DASH:
                // VOD DASH request.
                request = sdkFactory.createVodStreamRequest(
                        TEST_DASH_CONTENT_SOURCE_ID,
                        TEST_DASH_VIDEO_ID,
                        null); // apiKey
                request.setFormat(StreamFormat.DASH);
                return request;
            default:
                // Content type not selected.
                return null;
        }
    }

    private VideoStreamPlayer createVideoStreamPlayer() {
        return new VideoStreamPlayer() {
            @Override
            public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
                videoPlayer.setStreamUrl(url);
                videoPlayer.play();
            }

            @Override
            public int getVolume() {
                // Make the video player play at the current device volume.
                return 100;
            }

            @Override
            public void addCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                videoPlayerCallbacks.add(videoStreamPlayerCallback);
            }

            @Override
            public void removeCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                videoPlayerCallbacks.remove(videoStreamPlayerCallback);
            }

            @Override
            public void onAdBreakStarted() {
                // Disable player controls.
                videoPlayer.enableControls(false);
                log("Ad Break Started\n");
            }

            @Override
            public void onAdBreakEnded() {
                // Re-enable player controls.
                videoPlayer.enableControls(true);
                log("Ad Break Ended\n");
            }

            @Override
            public void onAdPeriodStarted() {
                log("Ad Period Started\n");
            }

            @Override
            public void onAdPeriodEnded() {
                log("Ad Period Ended\n");
            }

            @Override
            public void seek(long timeMs) {
                // An ad was skipped. Skip to the content time.
                videoPlayer.seekTo(timeMs);
                log("seek");
            }

            @Override
            public VideoProgressUpdate getContentProgress() {
                return new VideoProgressUpdate(
                    videoPlayer.getCurrentPositionPeriod(), videoPlayer.getDuration()
                );
            }
        };
    }

    /**
     * AdErrorListener implementation
     **/
    @Override
    public void onAdError(AdErrorEvent event) {
        log(String.format("Error: %s\n", event.getError().getMessage()));
        // play fallback URL.
        log("Playing fallback Url\n");
        videoPlayer.setStreamUrl(fallbackUrl);
        videoPlayer.enableControls(true);
        videoPlayer.play();
    }

    /**
     * AdEventListener implementation
     **/
    @Override
    public void onAdEvent(AdEvent event) {
        switch (event.getType()) {
            case AD_PROGRESS:
                checkAndInjectAdProgressInfo(streamManager.getAdProgressInfo());
                break;
            case STARTED:
                checkAndStartInteractiveAd(event.getAd());
                break;
            case COMPLETED:
                checkAndStopInteractiveAd();
                break;
            default:
                describeAd(event);
        }
    }

    private void describeAd(AdEvent event) {
        Ad ad = event.getAd();

        if (ad != null) {
            AdPodInfo pod = ad.getAdPodInfo();
            boolean hasCompanions = false;

            try {
                hasCompanions = (ad.getCompanionAds() != null && ad.getCompanionAds().size() > 0);
            } catch (Exception e) {}

            log(String.format("Event: %s, Pod %s, Ad(%s, %s)-- has companions: %s ", event.getType(), pod.getPodIndex(), pod.getAdPosition(), pod.getTotalAds(), hasCompanions));
        } else {
            log(String.format("Event: %s\n", event.getType()));
        }
    }

    private void describeAdProgress(AdProgressInfo info) {
        log(String.format("Ad(%s, %s) -- %s ____ %s", info.getAdPosition(), info.getTotalAds(), info.getCurrentTime(), info.getDuration()));
    }

    /**
     * AdsLoadedListener implementation
     **/
    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
        streamManager = event.getStreamManager();
        streamManager.addAdErrorListener(this);
        streamManager.addAdEventListener(this);
        streamManager.init();
    }

    /**
     * Sets fallback URL in case ads stream fails.
     **/
    void setFallbackUrl(String url) {
        fallbackUrl = url;
    }

    /**
     * Sets logger for displaying events to screen. Optional.
     **/
    void setLogger(Logger logger) {
        this.logger = logger;
    }

    void setAdvertisingId(String value) {
        advertisingId = value;
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    private void checkAndStartInteractiveAd(Ad ad) {
        disposeCurrentInteractiveAd();

        CompanionAd iAd = findInteractiveAdInfo(ad.getCompanionAds());

        if (iAd == null) {
            return;
        }

        // remove default ad position indicator
        adUiContainer.setVisibility(View.GONE);

        // create and start innovid ad
        innovidAdWrapper = new InnovidAdWrapper(webView, iAd, advertisingId);
        innovidAdWrapper.setAdEventListener(new InnovidAdWrapper.InnovidAdEventListener() {
            @Override
            public void onInnovidAdEvent(InnovidAdWrapper.InnovidAdEventType type) {
                logger.log(String.format("onInnovidAdEvent(%s)", type));
            }
        });

        innovidAdWrapper.setAdPlaybackRequestListener(new InnovidAdWrapper.InnovidAdPlaybackRequestListener() {

            @Override
            public void onPauseRequest() {
                logger.log("onPauseRequest()");
                videoPlayer.pause();
                videoPlayerContainer.post(() -> videoPlayerContainer.setVisibility(View.INVISIBLE));
            }

            @Override
            public void onResumeRequest() {
                logger.log("onResumeRequest()");
                videoPlayerContainer.post(() -> videoPlayerContainer.setVisibility(View.VISIBLE));

                videoPlayer.resume();
            }

            @Override
            public void onStopAndRestartOnNextResumeRequest() {
                logger.log("onStopAndRestartOnNextResumeRequest()");
            }
        });
        innovidAdWrapper.start();
    }

    private void checkAndStopInteractiveAd() {
        if (innovidAdWrapper == null) {
            return;
        }

        innovidAdWrapper.requestStop();
        innovidAdWrapper = null;
    }

    private void checkAndInjectAdProgressInfo(AdProgressInfo info) {
        if (innovidAdWrapper != null && videoPlayer.getPlaybackState() != SampleVideoPlayer.STOPPED) {
            final InnovidAdWrapper.SSAIPlaybackState playbackState = videoPlayer.getPlaybackState() == SampleVideoPlayer.PLAYING
                    ? InnovidAdWrapper.SSAIPlaybackState.PLAYING
                    : InnovidAdWrapper.SSAIPlaybackState.PAUSED
            ;

            innovidAdWrapper.injectPlaybackProgressInfo(
                    playbackState, info.getCurrentTime(), info.getDuration()
            );
        }
    }

    private void disposeCurrentInteractiveAd() {
        if (innovidAdWrapper == null) {
            return;
        }

        innovidAdWrapper.enforceStop();
        innovidAdWrapper = null;
    }

    private CompanionAd findInteractiveAdInfo(List<CompanionAd> companionAds) {
        if (companionAds == null) {
            return null;
        }

        CompanionAd result = null;

        for (CompanionAd companionAd : companionAds) {
            if (isCompanionAdSupported( companionAd )) {
                result = companionAd;
                break;
            }
        }

        return result;
    }

    private static Boolean isCompanionAdSupported(CompanionAd companionAd) {
        String apiFramework = companionAd.getApiFramework();
        String url = companionAd.getResourceValue();

        return apiFramework != null
                && url != null
                && apiFramework.equalsIgnoreCase("innovid")
                && (url.contains(".html?") || url.contains("tag/get.php?tag="))
        ;
    }
}
