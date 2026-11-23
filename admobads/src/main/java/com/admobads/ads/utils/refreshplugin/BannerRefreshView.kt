package com.admobads.ads.utils.refreshplugin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.ads.googlebannerrefresh.BannerRefreshConfiguration
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
class BannerRefreshView
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
  private var _adUnitId: String? = null
  private var adSize: AdSize? = null
  private var hostActivity: Activity? = null
  private var configuration: BannerRefreshConfiguration? = null
  internal var bannerManager: BannerManager? = null
  internal var id = System.identityHashCode(this)

  /** The banner currently on screen. */
  internal var currentBannerAd: BannerAd? = null

  /** The [AdView] hosting [currentBannerAd]. */
  internal var currentBannerView: View? = null

  /** Returns the [ResponseInfo] of the displayed ad, or `null` until an ad has been shown. */
  val responseInfo: ResponseInfo?
    get() = currentBannerAd?.getResponseInfo()

  /**
   * Returns `true` if the current ad is a collapsible banner. Returns `false` until an ad is
   * displayed.
   */
  val isCollapsible: Boolean
    get() = currentBannerAd?.isCollapsible() == true

  /**
   * Returns `true` if the banner refresh view is ready to rotate and the buffer is empty. Returns
   * `false` otherwise.
   */
  val isLoading: Boolean
    get() = bannerManager?.bannerState == BannerManager.BannerState.READY_TO_ROTATE

  /** Receives load results for every banner loaded into the buffer. */
  var adLoadCallback: AdLoadCallback<BannerAd>? = null

  /** Receives impression, click, paid and full-screen events from the displayed banner. */
  var adEventCallback: BannerAdEventCallback? = null

  var adUnitId: String
    get() = checkNotNull(_adUnitId) { "adUnitId must not be null." }
    set(value) {
      if (_adUnitId == null) {
        _adUnitId = value
      } else {
        Log.w(Constants.TAG, "Ignoring new ad unit ID. It can only be set once.")
      }
    }

  /**
   * Loads and starts refreshing banner ads.
   *
   * @param extras Extras for the Google adapter, replacing what used to be on the
   *   `AdRequest.Builder`.
   * @param requestBuilderFactory Creates the request builder for each load. Use it to add other
   *   request settings such as targeting.
   */
  fun load(
    extras: Bundle = Bundle(),
    requestBuilderFactory: (String, AdSize) -> BannerAdRequest.Builder = { unitId, size ->
      BannerAdRequest.Builder(unitId, size)
    },
  ) {
    if (_adUnitId == null || adSize == null || configuration == null) {
      Log.e(
        Constants.TAG,
        "Cannot load ads: Required refresh parameters (adUnitId, adSize, configuration) are not all set.",
      )
      return
    }

    configuration?.let { it ->
      BannerRefreshLogger.log(
        BannerRefreshLogger.Event.Start,
        "Ads buffer size: ${it.adsBufferSize}, Refresh rate: ${it.defaultRefreshRateInSeconds}, Refresh overrides: ${it.adSourceRefreshRatesInSeconds}.",
        id,
      )

      bannerManager?.destroy()
      bannerManager = BannerManager(this, it, extras, requestBuilderFactory)
      bannerManager?.load(it.adsBufferSize)
    }
  }

  fun setConfiguration(configuration: BannerRefreshConfiguration) {
    if (this.configuration == null) {
      this.configuration = configuration
    } else {
      Log.w(Constants.TAG, "Ignoring new configuration. It can only be set once.")
    }
  }

  fun getConfiguration(): BannerRefreshConfiguration? = configuration

  fun setHostActivity(activity: Activity) {
    hostActivity = activity
  }

  fun setAdSize(adSize: AdSize) {
    if (this.adSize == null) {
      this.adSize = adSize
    } else {
      Log.w(Constants.TAG, "Ignoring new ad size. It can only be set once.")
    }
  }

  fun getAdSize(): AdSize? = adSize

  internal fun display(ad: BannerAd) {
    val activity = hostActivity ?: findActivity()
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
      Log.e(Constants.TAG, "Cannot display banner: view is not attached to an Activity context.")
      ad.destroy()
      return
    }

    val previous = currentBannerAd
    releaseCurrentAdView()

    val heightPx = bannerHeightPx(ad)
    // AdView must be created with an Activity and added to the hierarchy before
    // registerBannerAd, or Next-Gen leaves an empty sized slot.
    val adView =
      AdView(activity).apply {
        layoutParams =
          LayoutParams(
            LayoutParams.MATCH_PARENT,
            if (heightPx > 0) heightPx else LayoutParams.WRAP_CONTENT,
          )
      }
    if (heightPx > 0) {
      minimumHeight = heightPx
    }
    addView(adView)
    adView.registerBannerAd(ad, activity)

    currentBannerAd = ad
    currentBannerView = adView
    visibility = VISIBLE
    requestLayout()

    if (previous != null && previous !== ad) {
      previous.destroy()
    }
  }

  private fun releaseCurrentAdView() {
    (currentBannerView as? AdView)?.unregisterBannerAd()
    removeAllViews()
    currentBannerView = null
    currentBannerAd = null
  }

  private fun bannerHeightPx(ad: BannerAd): Int {
    val fromAd = runCatching { ad.getAdSize().getHeightInPixels(context) }.getOrDefault(0)
    if (fromAd > 0) return fromAd
    val fromRequest = adSize?.getHeightInPixels(context) ?: 0
    return if (fromRequest > 0) fromRequest else 0
  }

  private fun findActivity(): Activity? {
    hostActivity?.let { return it }
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
      if (ctx is Activity) return ctx
      ctx = ctx.baseContext
    }
    return null
  }

  fun destroy() {
    bannerManager?.destroy()
    bannerManager = null
    releaseCurrentAdView()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
  }

  companion object {
    var isDebugLoggingEnabled = true
  }
}
