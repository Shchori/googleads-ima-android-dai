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

**Important** Make sure to define your PlayerView ```surface_type="texture_view"```
[Example][instantiate_iroll link]
    

## 4. Respond to renderer `playback-request` events
For SSAI, Innovid does not control video playback. This means that for formats that would normally affect stream playback we pass events that should be listened and responded to correctly. For pause, you should pause video playback until resume is provided.

- `onPauseRequest` [Example][handle_playback_pause_request link]
- `onResumeRequest` [Example][handle_playback_resume_request link]

## 5. Provide playback updates
In order to accurately track video progress, you must provide regular playback updates to the Innovid ad object on IMA `AD_PROGRESS event` in `onAdEvent`

[Example][handle_playback_updates link]

[listen_ima_events link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L289
[parse_ad_info link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L441      
[instantiate_iroll link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L368
[handle_playback_pause_request link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L380
[handle_playback_resume_request link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L387
[handle_playback_updates link]: app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/SampleAdsWrapper.java#L419
