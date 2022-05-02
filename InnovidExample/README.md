# Overview

This project contains sample source code that demonstrates how to integrate the Innovid iRoll 
with the Google IMA DAI SDK in Android. This document will step through the various pieces of code 
that make the integration work, so that the same basic ideas can be replicated in a real production app.

# Flow

## 1. Listen to IMA SDK `AdEvent.STARTED` event
On the IMA event `STARTED` you should confirm that an Innovid interactive ad is being returned

[Example][listen_ima_events link]
    
## 2. Look for Innovid companions for a given ad
In the handler method for IMA `AdEvent.STARTED` event, we inspect `Ad#companions` property. 
If any companion has an `apiFramework` value matching `innovid` and is an HTML file, then we ignore all other companions and begin Innovid iRoll™.

[Example][parse_ad_info link]

## 3. Initialize and start the renderer
Once we have the **companion url**, we can initialize the innovid ads wrapper 
and set playback listener and ad events listener. Make sure to pass the webview, companion URL, and advertising ID.

[Example][instantiate_iroll link]

```
mInteractiveAd = new InnovidAdWrapper(mWebView, iAd, mAdvertisingId);
```

Once the wrapper is initialized and event listeners are set, call `start()` to begin
```
mInteractiveAd.start();
```
    

## 4. Respond to renderer `playback-request` events
For SSAI, Innovid does not control video playback. This means that for formats that would normally affect stream playback we pass events that should be listened and responded to correctly. For pause, you should pause video playback until resume is provided. To support older devices running Fire OS 6 or below, your host app's video player container should be hidden due to resource limitations/bugs in the host operating system. 

 - `onPauseRequest` [Example][handle_playback_pause_request link]

Sample:
```
@Override
public void onPauseRequest() {
    logger.log("onPauseRequest()");
    videoPlayer.pause();
    videoPlayerContainer.post(() -> videoPlayerContainer.setVisibility(View.INVISIBLE));
}
```

- `onResumeRequest` [Example][handle_playback_resume_request link]

```
@Override
public void onResumeRequest() {
    logger.log("onResumeRequest()");
    videoPlayerContainer.post(() -> videoPlayerContainer.setVisibility(View.VISIBLE));

    videoPlayer.resume();
}
```
## 5. Provide playback updates
In order to accurately track video progress, you must provide regular playback updates to the Innovid ad object on IMA `AD_PROGRESS event` in `onAdEvent`

[Example][handle_playback_updates link]

## 6. Respond to Innovid Ad Events
If you would like to respond to Innovid Ad Events, you can set up an event listener as described below

[Example][handle_innovid_ad_events_link]
```
        mInteractiveAd.setAdEventListener(new InnovidAdWrapper.InnovidAdEventListener() {
            @Override
            public void onInnovidAdEvent(InnovidAdWrapper.InnovidAdEventType type) {
                mLogger.log(String.format("onInnovidAdEvent(%s)", type));
            }
        });
```
For example you may want to fire specific third party pixels on standard ad events (impressions or quartiles)

Additionally, Innovid will pass an event "iroll-back-pressed" to the host application when we detect the back button has been pressed and should be handled by the application. Handling of this event is left to the discretion of the host application, but generally this is where the application would exit stream playback and return the user to content selection or some other screen.

## 7. Innovid Wrapper
The Innovid wrapper is a sample implementation that allows running Innovid interactive ads. In this wrapper you will define the platform being run on (fireTV, xbox, playstation etc.), provide a keymapping of the remote events, and generate the webview layer for the interactive portion

[Example][innovid_wrapper link]

The wrapper as indicated in step 3 should be provided with current webview, the companion mediafile URL, and advertising ID.

```    
public InnovidAdWrapper(WebView webView, CompanionAd adInfo, String advertisingId) {
        mWebView = webView;
        mAdInfo = adInfo;

        mStarted = false;
        mStopRequested = false;
        mAdvertisingId = advertisingId;
    }
```
## 8. Starting playback
Once start is called we configure the webview and it's javascript interface before loading the companion mediafile into the webview to render the interactive layer.

After creating the webview, we load in the URL and request focus

```
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
```

In this example, we are manually generating a hardcoded interactive url in the `generateIAdUrl()` function via `final String tagUrl = "http://tv.innovid.com/tag/get.php?tag=1maft2&kind=placement&device=ctv-html";`. In live instance or if testing against an actual ad response please use `final String tagUrl = mAdInfo.getResourceValue();`


```
    private String generateIAdUrl() {
        String encodedParams = "";

    //     final String tagUrl = mAdInfo.getResourceValue();
        final String tagUrl = "http://tv.innovid.com/tag/get.php?tag=1maft2&kind=placement&device=ctv-html";

        try {
            encodedParams = URLEncoder.encode(getIAdStartupParametersAsString(), "UTF-8");
        } catch (UnsupportedEncodingException ignored) {}

        return String.format("%s#params=%s", tagUrl, encodedParams);
    }
 ```   

## 9. Configuring the WebView and Interface
When configuring the webview, make sure to enable javascript, use wide viewport, remove zoom controls, and allow for focus. This is also where you should configure a keyListener to accept back buttons to allow for collapsing webview

[Example][configureWebView link]

## 10. Keymap
In order to process key events, you will need to create a map of action value pairs in a JSON and pass this to the Innovid ad object.
This is also where device and advertising ID are passed along. This information is encoded and appended to the companion URL that is loaded into the webview.
If using SSAI, it is mandatory that you define "ssai" as true when defining startup parameters.
```
json.put("ssai", true);
```
[Example][keymap link]


    

[listen_ima_events link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L289
[parse_ad_info link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L441      
[instantiate_iroll link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L355
[handle_playback_pause_request link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L380
[handle_innovid_ad_events_link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L369
[handle_playback_resume_request link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L387
[handle_playback_updates link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L419
[innovid_wrapper link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/InnovidAdWrapper.java
[configureWebView link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/InnovidAdWrapper.java#L138
[keymap link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/InnovidAdWrapper.java#L231
