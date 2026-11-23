package com.admobads.ads.utils.refreshplugin

import android.os.Bundle
import com.google.android.ads.googlebannerrefresh.AdUtil
import com.google.android.ads.googlebannerrefresh.BannerRefreshConfiguration
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the loading and display of banner ads, including caching and refreshing.
 * Tied to a specific BannerRefreshView.
 */
class BannerManager(
  private val bannerRefreshView: BannerRefreshView,
  private val configuration: BannerRefreshConfiguration,
  /** Extras you previously put on the AdRequest.Builder for the Google/AdMob adapter. */
  private val baseExtras: Bundle = Bundle(),
  /** Builder is created per request because adUnitId and adSize are now required up front. */
  private val adRequestBuilderFactory: (String, AdSize) -> BannerAdRequest.Builder =
    { adUnitId, adSize -> BannerAdRequest.Builder(adUnitId, adSize) },
) {

  internal enum class BannerState {
    READY_TO_ROTATE,
    WAITING_FOR_IMPRESSION,
    ACTIVE,
  }

  internal val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  internal val bannerCache = BannerCache()
  internal val refreshBackoff =
    BannerRefreshBackoff(configuration.adsBufferSize, configuration.defaultRefreshRateInSeconds)
  internal val idleTimerController = IdleTimerController()
  internal var bannerState: BannerState = BannerState.READY_TO_ROTATE
  internal var rotationJob: Job? = null

  fun load(
    bannersToLoad: Int = 1,
    previousRefreshRateInSeconds: Int? = null,
    previousIdleTimeInSeconds: Int? = null,
    previousAdSourceId: String? = null,
  ) {
    repeat(bannersToLoad) { index ->
      val adRequest =
        if (index == 0) {
          // Only the first request in the batch carries the previous banner analytics.
          buildAdRequest(previousRefreshRateInSeconds, previousIdleTimeInSeconds, previousAdSourceId)
        } else {
          buildAdRequest()
        }
      if (adRequest == null) return // No ad size yet.
      loadAd(adRequest)
    }
  }

  /** Starts one load. The BannerAd only exists once it has loaded, so there is no unloaded list. */
  private fun loadAd(adRequest: BannerAdRequest) {
    BannerRefreshLogger.log(BannerRefreshLogger.Event.LoadAd, "", bannerRefreshView.id)
    BannerAd.load(
      adRequest,
      object : AdLoadCallback<BannerAd> {
        override fun onAdLoaded(ad: BannerAd) = handleAdLoaded(ad)

        override fun onAdFailedToLoad(adError: LoadAdError) = handleAdFailedToLoad(adError)
      },
    )
  }

  private fun buildAdRequest(
    previousRefreshRateInSeconds: Int? = null,
    previousIdleTimeInSeconds: Int? = null,
    previousAdSourceId: String? = null,
  ): BannerAdRequest? {
    val adSize = bannerRefreshView.getAdSize() ?: return null

    // Copy so we never mutate the caller's bundle between requests.
    val extras = Bundle(baseExtras)

    extras.putInt("_noRefresh", 1) // Disable autorefresh.
    extras.putString("google-banner-refresh", Constants.VERSION)
    extras.putInt(ADS_BUFFER_SIZE_KEY, configuration.adsBufferSize)
    extras.putInt(DEFAULT_REFRESH_RATE_KEY, configuration.defaultRefreshRateInSeconds)
    val adSourceRefreshRatesBundle = Bundle()
    configuration.adSourceRefreshRatesInSeconds.forEach { (key, value) ->
      adSourceRefreshRatesBundle.putInt(key, value)
    }
    extras.putBundle(REFRESH_RATE_OVERRIDES_KEY, adSourceRefreshRatesBundle)

    if (previousRefreshRateInSeconds != null) {
      extras.putInt(PREVIOUS_REFRESH_RATE_KEY, previousRefreshRateInSeconds)
    }
    if (previousIdleTimeInSeconds != null) {
      extras.putInt(PREVIOUS_IDLE_TIME_KEY, previousIdleTimeInSeconds)
    }
    if (previousAdSourceId != null) {
      extras.putString(PREVIOUS_AD_SOURCE_ID_KEY, previousAdSourceId)
    }

    return adRequestBuilderFactory(bannerRefreshView.adUnitId, adSize)
      .setGoogleExtrasBundle(extras)
      // If your SDK version exposes a request agent setter, set
      // "google-banner-refresh-${Constants.VERSION}" here.
      .build()
  }

  private fun handleAdLoaded(ad: BannerAd) {
    scope.launch {
      if (!isActive) { // destroy() was called while this load was in flight.
        ad.destroy()
        return@launch
      }
      ad.adEventCallback = createAdEventCallback(ad)

      refreshBackoff.resetBackoff()
      bannerCache.addLoaded(ad)

      val info = ad.getResponseInfo().loadedAdSourceResponseInfo
      BannerRefreshLogger.log(
        BannerRefreshLogger.Event.LoadAdSuccess,
        "Ad source name: ${info?.name ?: ""}, Ad source ID: ${info?.id ?: ""}, Banners in cache:, ${bannerCache.getLoadedSize()}.",
        bannerRefreshView.id,
      )

      if (bannerState == BannerState.READY_TO_ROTATE && bannerCache.getLoadedSize() > 0) {
        tryRotateBannerView()
      }

      // Only notify the UI after a creative is actually attached. Otherwise shimmer
      // hides and leaves an empty banner-sized gap.
      if (bannerRefreshView.currentBannerAd != null) {
        bannerRefreshView.adLoadCallback?.onAdLoaded(ad)
      }
    }
  }

  private fun handleAdFailedToLoad(adError: LoadAdError) {
    scope.launch {
      bannerRefreshView.adLoadCallback?.onAdFailedToLoad(adError)

      val currentBackoffInSeconds = refreshBackoff.getCurrentBackoffInSeconds()
      BannerRefreshLogger.log(
        BannerRefreshLogger.Event.LoadAdFailure,
        "${currentBackoffInSeconds}s until next retry.",
        bannerRefreshView.id,
      )

      refreshBackoff.increaseBackoff()
      delay(currentBackoffInSeconds * 1000L)

      // A failed load leaves nothing to reload, so start a fresh load.
      buildAdRequest()?.let { loadAd(it) }
    }
  }

  /** Rotates the displayed banner by taking one from the cache and loads a replacement. */
  internal suspend fun tryRotateBannerView() {
    bannerState = BannerState.READY_TO_ROTATE

    val nextBannerAd = bannerCache.popLoadedBannerAd()
    if (nextBannerAd == null) {
      idleTimerController.start()
      BannerRefreshLogger.log(
        BannerRefreshLogger.Event.RotateFailure,
        "Starting idle timer.",
        bannerRefreshView.id,
      )
      return
    }

    val previousBannerAd = bannerRefreshView.currentBannerAd

    bannerState = BannerState.WAITING_FOR_IMPRESSION
    bannerRefreshView.display(nextBannerAd)

    val previousIdleTimeInSeconds = idleTimerController.idleTimeInSeconds
    BannerRefreshLogger.log(
      BannerRefreshLogger.Event.RotateSuccess,
      "Idle time: ${previousIdleTimeInSeconds}s, Banners in cache: ${bannerCache.getLoadedSize()}.",
      bannerRefreshView.id,
    )

    if (previousBannerAd == null) {
      load()
    } else {
      val previousAdSourceId = previousBannerAd.getResponseInfo().loadedAdSourceResponseInfo?.id
      val previousRefreshRateInSeconds: Int =
        if (previousAdSourceId != null) {
          configuration.adSourceRefreshRatesInSeconds[previousAdSourceId]
            ?: configuration.defaultRefreshRateInSeconds
        } else {
          configuration.defaultRefreshRateInSeconds
        }
      load(
        previousRefreshRateInSeconds = previousRefreshRateInSeconds,
        previousIdleTimeInSeconds = previousIdleTimeInSeconds,
        previousAdSourceId = previousAdSourceId,
      )
    }
    idleTimerController.resetAndStopTimer()

    val nextAdSourceId = nextBannerAd.getResponseInfo().loadedAdSourceResponseInfo?.id

    // Only run the visibility check for third-party ads.
    if (
      nextAdSourceId != AdSource.ADMOB_NETWORK && nextAdSourceId != AdSource.ADMOB_NETWORK_WATERFALL
    ) {
      while (!isBannerVisible()) {
        delay(5000) // Check every 5s.
      }
      scheduleRotation(nextBannerAd)
    }
  }

  private fun isBannerVisible(): Boolean =
    bannerRefreshView.currentBannerView?.let { AdUtil.isAdViewVisible(it) } == true

  internal fun scheduleRotation(ad: BannerAd) {
    // Only the ad currently shown in the BannerRefreshView can schedule a rotation.
    if (ad !== bannerRefreshView.currentBannerAd) return
    if (rotationJob?.isActive == true) return

    val info = ad.getResponseInfo().loadedAdSourceResponseInfo
    val adSourceName = info?.name
    val adSourceId = info?.id

    val (refreshRateInSeconds, refreshRateSource) =
      run {
        val overrideRate = adSourceId?.let { configuration.adSourceRefreshRatesInSeconds[it] }
        if (overrideRate == null) {
          Pair(configuration.defaultRefreshRateInSeconds, "Default")
        } else {
          Pair(overrideRate, "Override")
        }
      }

    bannerState = BannerState.ACTIVE
    BannerRefreshLogger.log(
      BannerRefreshLogger.Event.AdVisible,
      "Ad source name: ${adSourceName ?: ""}, Ad source ID: ${adSourceId ?: ""}, Refresh rate for ad source ID: ${refreshRateInSeconds}, Refresh rate source: ${refreshRateSource}.",
      bannerRefreshView.id,
    )

    rotationJob =
      scope.launch {
        delay(refreshRateInSeconds * 1000L)
        rotationJob = null
        tryRotateBannerView()
      }
  }

  /** Per-ad event callback; replaces the legacy AdListener and OnPaidEventListener. */
  internal fun createAdEventCallback(ad: BannerAd): BannerAdEventCallback {
    return object : BannerAdEventCallback {
      override fun onAdImpression() {
        scope.launch {
          scheduleRotation(ad)
          bannerRefreshView.adEventCallback?.onAdImpression()
        }
      }

      override fun onAdClicked() {
        bannerRefreshView.adEventCallback?.onAdClicked()
      }

      override fun onAdPaid(value: AdValue) {
        bannerRefreshView.adEventCallback?.onAdPaid(value)
      }

      // Replaces onAdOpened / onAdClosed.
      override fun onAdShowedFullScreenContent() {
        bannerRefreshView.adEventCallback?.onAdShowedFullScreenContent()
      }

      override fun onAdDismissedFullScreenContent() {
        bannerRefreshView.adEventCallback?.onAdDismissedFullScreenContent()
      }

      override fun onAdFailedToShowFullScreenContent(
        fullScreenContentError: FullScreenContentError
      ) {
        bannerRefreshView.adEventCallback?.onAdFailedToShowFullScreenContent(fullScreenContentError)
      }
    }
  }

  fun destroy() {
    rotationJob?.cancel()
    rotationJob = null
    // bannerCache.destroy() is suspend, so cancel the scope only after it finishes.
    scope.launch {
      bannerCache.destroy()
      scope.cancel()
    }
    idleTimerController.destroy()
  }

  internal companion object {
    const val ADS_BUFFER_SIZE_KEY = "brb"
    const val DEFAULT_REFRESH_RATE_KEY = "brrd"
    const val REFRESH_RATE_OVERRIDES_KEY = "brro"
    const val PREVIOUS_REFRESH_RATE_KEY = "brrp"
    const val PREVIOUS_AD_SOURCE_ID_KEY = "brpas"
    const val PREVIOUS_IDLE_TIME_KEY = "brpi"
  }
}