package com.admobads.ads

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.admobads.ads.utils.refreshplugin.AdSource
import com.google.android.ads.googlebannerrefresh.BannerRefreshConfiguration
import com.admobads.ads.utils.refreshplugin.BannerRefreshView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

class AdmobBannerRefresh(
    private val context: Activity,
    private val bannerAdContainer: FrameLayout
) {

    companion object {
        private const val TAG = "AdmobBannerRefresh"
    }

    private var bannerRefreshView: BannerRefreshView? = null
    private var adUnitId: String? = null
    private var adType: BannerAdType = BannerAdType.STANDARD
    private var bannerPosition: BannerPosition = BannerPosition.BOTTOM
    private var loadingLayout: View? = null
    private var adsLayout: FrameLayout? = null
    private var skeletonConstraintLayout: View? = null
    private var skeletonColor: Int? = null
    private var backgroundColor: Int? = null
    private var isDestroyed = false


    fun setSkeletonColor(color: Int): AdmobBannerRefresh {
        skeletonColor = color

        runOnMainThread {
            skeletonConstraintLayout?.setBackgroundColor(color)
        }

        return this
    }

    fun setBackgroundColor(color: Int): AdmobBannerRefresh {
        backgroundColor = color

        runOnMainThread {
            adsLayout?.setBackgroundColor(color)
        }

        return this
    }

    fun loadBannerAd(
        adUnitId: String,
        ad_refresh: Int,
        ad_buffer: Int,
        adType: Int,
        position: BannerPosition = BannerPosition.BOTTOM
    ) {
        val type = when (adType) {
            1 -> BannerAdType.COLLAPSIBLE
            2 -> BannerAdType.STANDARD
            3 -> BannerAdType.MEDIUM_RECTANGLE
            4 -> BannerAdType.LARGE_BANNER
            else -> BannerAdType.STANDARD
        }

        loadBannerAd(adUnitId, ad_refresh, ad_buffer, type, position)
    }

    fun loadBannerAd(
        adUnitId: String,
        ad_refresh: Int,
        ad_buffer: Int,
        adType: BannerAdType = BannerAdType.STANDARD,
        position: BannerPosition = BannerPosition.BOTTOM
    ) {
        if (isDestroyed) {
            Log.w(TAG, "Cannot load banner after destroy().")
            return
        }

        this.adUnitId = adUnitId
        this.adType = adType
        this.bannerPosition = position

        runOnMainThread {
            setupViewHierarchy()
            initializeBannerRefreshView(ad_refresh, ad_buffer)
        }
    }
    private fun setupViewHierarchy() {
        destroyBannerView()

        bannerAdContainer.removeAllViews()

        val loadingResource = when (adType) {
            BannerAdType.STANDARD,
            BannerAdType.COLLAPSIBLE -> R.layout.tlib_banner_standard_loading

            BannerAdType.LARGE_BANNER -> R.layout.tlib_banner_full_loading

            BannerAdType.MEDIUM_RECTANGLE ->
                R.layout.tlib_banner_rectangle_loading
        }

        loadingLayout = try {
            android.view.LayoutInflater.from(context)
                .inflate(loadingResource, bannerAdContainer, false)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to inflate banner loading layout.", exception)
            null
        }

        loadingLayout?.let { loadingView ->

            skeletonConstraintLayout =
                loadingView.findViewById(R.id.skeletonLayout)

            skeletonColor?.let {
                skeletonConstraintLayout?.setBackgroundColor(it)
            }

            val loadingParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = getGravity()
            }

            bannerAdContainer.addView(loadingView, loadingParams)
        }

        adsLayout = FrameLayout(context).apply {
            visibility = View.GONE
            backgroundColor?.let { setBackgroundColor(it) }
        }

        val adsParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = getGravity()
        }

        bannerAdContainer.addView(adsLayout, adsParams)

        showLoadingState()
    }
    private fun initializeBannerRefreshView(
        ad_refresh: Int,
        ad_buffer: Int,
    ) {

        val unitId = adUnitId ?: return

        val refreshConfiguration =
            BannerRefreshConfiguration.Builder()
                .setAdsBufferSize(ad_buffer)
                .setDefaultRefreshRateInSeconds(ad_refresh)
                .setAdSourceRefreshRatesInSeconds(
                    mapOf(
                        AdSource.ADMOB_NETWORK to ad_refresh
                    )
                )
                .build()

        val bannerSize = getBannerAdSize()

        val refreshView = BannerRefreshView(context).apply {

            this.adUnitId = unitId

            setHostActivity(context as Activity)

            setAdSize(bannerSize)

            setConfiguration(refreshConfiguration)

            adLoadCallback = object : AdLoadCallback<BannerAd> {

                override fun onAdLoaded(ad: BannerAd) {
                    Log.d(TAG, "Banner ad loaded successfully.")

                    runOnMainThread {
                        showAdViews()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(
                        TAG,
                        "Banner ad failed to load: ${adError.message}"
                    )
                }
            }

            adEventCallback = object : BannerAdEventCallback {

                override fun onAdImpression() {
                    Log.d(TAG, "Banner impression recorded.")
                }

                override fun onAdClicked() {
                    Log.d(TAG, "Banner ad clicked.")
                }

                override fun onAdPaid(value: AdValue) {
                    Log.d(
                        TAG,
                        "Banner paid event: ${value.valueMicros} ${value.currencyCode}"
                    )

                    AdRevenueTracker.handlePaid(
                        adValue = value,
                        adUnitId = adUnitId,
                        adFormat = AdRevenueTracker.FORMAT_BANNER,
                        logTag = TAG
                    )
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Banner showed full-screen content.")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Banner full-screen content dismissed.")
                }

                override fun onAdFailedToShowFullScreenContent(
                    fullScreenContentError: FullScreenContentError
                ) {
                    Log.e(
                        TAG,
                        "Banner failed to show full-screen content: " +
                                fullScreenContentError.message
                    )
                }
            }
        }

        bannerRefreshView = refreshView

        adsLayout?.addView(
            refreshView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val extras = Bundle()

        if (adType == BannerAdType.COLLAPSIBLE) {
            extras.putString(
                "collapsible",
                when (bannerPosition) {
                    BannerPosition.TOP -> "top"
                    BannerPosition.BOTTOM -> "bottom"
                }
            )
        }

        refreshView.load(extras)
    }

    private fun getBannerAdSize(): AdSize {

        val widthDp = getAvailableWidthDp()

        return when (adType) {

            BannerAdType.STANDARD,
            BannerAdType.COLLAPSIBLE -> {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    widthDp
                )
            }

            BannerAdType.LARGE_BANNER -> {
                AdSize.LARGE_BANNER
            }

            BannerAdType.MEDIUM_RECTANGLE -> {
                AdSize.MEDIUM_RECTANGLE
            }
        }
    }

    private fun getAvailableWidthDp(): Int {

        val density = context.resources.displayMetrics.density

        val containerWidth = bannerAdContainer.width

        val widthPixels = if (containerWidth > 0) {
            containerWidth
        } else {
            context.resources.displayMetrics.widthPixels
        }

        return (widthPixels / density).toInt().coerceAtLeast(1)
    }

    private fun showLoadingState() {
        loadingLayout?.visibility = View.VISIBLE
        adsLayout?.visibility = View.GONE
    }

    private fun showAdViews() {
        if (isDestroyed) return

        loadingLayout?.visibility = View.GONE
        adsLayout?.visibility = View.VISIBLE

        bannerAdContainer.requestLayout()
    }

    private fun hideAllAdViews() {
        loadingLayout?.visibility = View.GONE
        adsLayout?.visibility = View.GONE
    }

    private fun getGravity(): Int {
        return when (bannerPosition) {
            BannerPosition.TOP -> Gravity.TOP
            BannerPosition.BOTTOM -> Gravity.BOTTOM
        }
    }


    private fun destroyBannerView() {
        bannerRefreshView?.let { refreshView ->
            try {
                refreshView.destroy()
            } catch (exception: Exception) {
                Log.e(TAG, "Error destroying banner refresh view.", exception)
            }
        }

        bannerRefreshView = null
    }

    fun destroy() {
        if (isDestroyed) return

        isDestroyed = true

        runOnMainThread {
            destroyBannerView()

            hideAllAdViews()

            bannerAdContainer.removeAllViews()

            loadingLayout = null
            adsLayout = null
            skeletonConstraintLayout = null
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (context.isFinishing || context.isDestroyed) return

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            context.runOnUiThread {
                if (!isDestroyed) {
                    action()
                }
            }
        }
    }
}
