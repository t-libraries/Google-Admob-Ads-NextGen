package com.admobads.ads

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.util.ArrayDeque

class AdmobBannerRefresh(
    private val activity: Activity,
    private val bannerContainer: FrameLayout,
    private val adUnitId: String,
    private val adSize: AdSize,
    buffer: Int = DEFAULT_BUFFER,
    refreshTime: Int = DEFAULT_REFRESH_TIME_SECONDS,
    adSourceRefreshTimes: Map<String, Int> = emptyMap(),
    private val baseExtras: Bundle = Bundle(),
    private val requestBuilderFactory: (String, AdSize) -> BannerAdRequest.Builder =
        { unitId, size -> BannerAdRequest.Builder(unitId, size) }
) {

    companion object {
        private const val TAG = "AdmobBannerRefresh"
        private const val DEFAULT_BUFFER = 3
        private const val DEFAULT_REFRESH_TIME_SECONDS = 120
        private const val MIN_BUFFER = 1
        private const val MAX_BUFFER = 10
        private const val MIN_REFRESH_TIME_SECONDS = 5
        private const val MAX_REFRESH_TIME_SECONDS = 150
        private const val VISIBILITY_CHECK_MS = 5_000L
        private const val VERSION = "1.7.0"

        private const val ADS_BUFFER_SIZE_KEY = "brb"
        private const val DEFAULT_REFRESH_RATE_KEY = "brrd"
        private const val REFRESH_RATE_OVERRIDES_KEY = "brro"
        private const val PREVIOUS_REFRESH_RATE_KEY = "brrp"
        private const val PREVIOUS_AD_SOURCE_ID_KEY = "brpas"
        private const val PREVIOUS_IDLE_TIME_KEY = "brpi"

        private const val ADMOB_NETWORK = "5450213213286189855"
        private const val ADMOB_NETWORK_WATERFALL = "1215381445328257950"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bufferSize = buffer.coerceIn(MIN_BUFFER, MAX_BUFFER)
    private val defaultRefreshTime = refreshTime.coerceIn(
        MIN_REFRESH_TIME_SECONDS,
        MAX_REFRESH_TIME_SECONDS
    )
    private val sourceRefreshTimes = adSourceRefreshTimes.mapValues { (_, seconds) ->
        seconds.coerceIn(MIN_REFRESH_TIME_SECONDS, MAX_REFRESH_TIME_SECONDS)
    }.toMutableMap().apply {
        this[ADMOB_NETWORK]?.let { this[ADMOB_NETWORK_WATERFALL] = it }
    }

    private val loadedAds = ArrayDeque<BannerAd>()

    private var currentAd: BannerAd? = null
    private var currentAdView: AdView? = null
    private var inFlightLoads = 0

    private var isStarted = false
    private var isDestroyed = false
    private var waitingForBufferedAd = false

    private var rotationDeadline = 0L
    private var remainingRefreshTimeMs = 0L
    private var idleStartedAt = 0L
    private var idleAccumulatedMs = 0L
    private var scheduledRetries = 0

    private var previousRefreshTimeSeconds: Int? = null
    private var previousIdleTimeSeconds: Int? = null
    private var previousAdSourceId: String? = null

    var adEventCallback: BannerAdEventCallback? = null

    var adLoadCallback: AdLoadCallback<BannerAd>? = null

    private val rotationRunnable = object : Runnable {
        override fun run() {
            if (isDestroyed || !isStarted) return

            val displayedAd = currentAd ?: return
            if (rotationDeadline > 0L && SystemClock.elapsedRealtime() < rotationDeadline) {
                mainHandler.postDelayed(this, rotationDeadline - SystemClock.elapsedRealtime())
                return
            }

            if (!isBannerVisible()) {
                // Keep the expired refresh pending until the displayed banner is visible again.
                rotationDeadline = SystemClock.elapsedRealtime() + VISIBILITY_CHECK_MS
                mainHandler.postDelayed(this, VISIBILITY_CHECK_MS)
                return
            }

            if (displayedAd === currentAd) {
                rotateToNextAd()
            }
        }
    }

    init {
        require(adUnitId.isNotBlank()) { "adUnitId cannot be blank." }
    }

    fun load(): AdmobBannerRefresh {
        runOnMainThread {
            if (isDestroyed) return@runOnMainThread
            isStarted = true
            fillBuffer()
        }
        return this
    }

    fun pause() {
        runOnMainThread {
            if (isDestroyed || !isStarted) return@runOnMainThread

            isStarted = false
            if (idleStartedAt > 0L) {
                idleAccumulatedMs +=
                    (SystemClock.elapsedRealtime() - idleStartedAt).coerceAtLeast(0L)
                idleStartedAt = 0L
            }
            if (rotationDeadline > 0L) {
                remainingRefreshTimeMs =
                    (rotationDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            }
            rotationDeadline = 0L
            mainHandler.removeCallbacks(rotationRunnable)
        }
    }

    fun resume() {
        runOnMainThread {
            if (isDestroyed || isStarted) return@runOnMainThread

            isStarted = true
            if (waitingForBufferedAd && idleStartedAt == 0L) {
                idleStartedAt = SystemClock.elapsedRealtime()
            }

            if (waitingForBufferedAd && loadedAds.isNotEmpty()) {
                rotateToNextAd()
            } else if (remainingRefreshTimeMs > 0L && currentAd != null) {
                scheduleRotation(remainingRefreshTimeMs)
            } else {
                fillBuffer()
            }
        }
    }

    fun getBufferedAdCount(): Int = loadedAds.size

    fun hasDisplayedAd(): Boolean = currentAd != null

    fun destroy() {
        runOnMainThread {
            if (isDestroyed) return@runOnMainThread

            isDestroyed = true
            isStarted = false
            mainHandler.removeCallbacksAndMessages(null)

            currentAdView?.unregisterBannerAd()
            bannerContainer.removeAllViews()
            currentAdView = null

            currentAd?.destroy()
            currentAd = null

            while (loadedAds.isNotEmpty()) {
                loadedAds.removeFirst().destroy()
            }

            inFlightLoads = 0
            scheduledRetries = 0
            waitingForBufferedAd = false
            idleStartedAt = 0L
            idleAccumulatedMs = 0L
        }
    }

    private fun fillBuffer() {
        if (isDestroyed) return

        val totalAds =
            (if (currentAd == null) 0 else 1) +
                    loadedAds.size + inFlightLoads + scheduledRetries
        val requestsNeeded = (bufferSize - totalAds).coerceAtLeast(0)

        repeat(requestsNeeded) {
            loadOneAd()
        }
    }

    private fun loadOneAd() {
        if (isDestroyed) return

        val request = buildAdRequest() ?: return
        inFlightLoads++

        Log.d(TAG, "Loading banner. In-flight: $inFlightLoads, cached: ${loadedAds.size}")

        BannerAd.load(
            request,
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    runOnMainThread {
                        inFlightLoads = (inFlightLoads - 1).coerceAtLeast(0)

                        if (isDestroyed || activity.isFinishing || activity.isDestroyed) {
                            ad.destroy()
                            return@runOnMainThread
                        }

                        ad.adEventCallback = createAdEventCallback(ad)
                        currentBackoffSeconds = 1
                        loadedAds.addLast(ad)

                        Log.d(TAG, "Banner loaded. Cached ads: ${loadedAds.size}")
                        adLoadCallback?.onAdLoaded(ad)

                        if (currentAd == null) {
                            displayNextAd()
                        } else if (waitingForBufferedAd && isStarted) {
                            rotateToNextAd()
                        }

                        fillBuffer()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    runOnMainThread {
                        inFlightLoads = (inFlightLoads - 1).coerceAtLeast(0)
                        Log.w(TAG, "Banner load failed: $adError")
                        adLoadCallback?.onAdFailedToLoad(adError)

                        scheduleRetry()
                    }
                }
            }
        )
    }

    private fun buildAdRequest(): BannerAdRequest {
        val extras = Bundle(baseExtras).apply {
            putInt("_noRefresh", 1)
            putString("google-banner-refresh", VERSION)
            putInt(ADS_BUFFER_SIZE_KEY, bufferSize)
            putInt(DEFAULT_REFRESH_RATE_KEY, defaultRefreshTime)

            val overrides = Bundle()
            sourceRefreshTimes.forEach { (sourceId, seconds) ->
                overrides.putInt(sourceId, seconds)
            }
            putBundle(REFRESH_RATE_OVERRIDES_KEY, overrides)

            previousRefreshTimeSeconds?.let { putInt(PREVIOUS_REFRESH_RATE_KEY, it) }
            previousIdleTimeSeconds?.let { putInt(PREVIOUS_IDLE_TIME_KEY, it) }
            previousAdSourceId?.let { putString(PREVIOUS_AD_SOURCE_ID_KEY, it) }
        }

        previousRefreshTimeSeconds = null
        previousIdleTimeSeconds = null
        previousAdSourceId = null

        return requestBuilderFactory(adUnitId, adSize)
            .setGoogleExtrasBundle(extras)
            .build()
    }

    private fun createAdEventCallback(ad: BannerAd): BannerAdEventCallback {
        return object : BannerAdEventCallback {
            override fun onAdImpression() {
                runOnMainThread {
                    if (ad === currentAd) {
                        val sourceId = getAdSourceId(ad)
                        val refreshSeconds = sourceId?.let { sourceRefreshTimes[it] }
                            ?: defaultRefreshTime
                        scheduleRotation(refreshSeconds * 1000L)
                    }
                    adEventCallback?.onAdImpression()
                }
            }

            override fun onAdClicked() {
                runOnMainThread { adEventCallback?.onAdClicked() }
            }

            override fun onAdPaid(value: AdValue) {
                runOnMainThread {
                    AdRevenueTracker.handlePaid(
                        adValue = value,
                        adUnitId = adUnitId,
                        adFormat = AdRevenueTracker.FORMAT_BANNER,
                        logTag = TAG
                    )
                    adEventCallback?.onAdPaid(value)
                }
            }

            override fun onAdShowedFullScreenContent() {
                runOnMainThread { adEventCallback?.onAdShowedFullScreenContent() }
            }

            override fun onAdDismissedFullScreenContent() {
                runOnMainThread { adEventCallback?.onAdDismissedFullScreenContent() }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                runOnMainThread {
                    adEventCallback?.onAdFailedToShowFullScreenContent(fullScreenContentError)
                }
            }
        }
    }

    private fun scheduleRotation(delayMs: Long) {
        if (isDestroyed || currentAd == null) return

        mainHandler.removeCallbacks(rotationRunnable)
        remainingRefreshTimeMs = delayMs
        rotationDeadline = 0L

        if (isStarted) {
            rotationDeadline = SystemClock.elapsedRealtime() + delayMs
            mainHandler.postDelayed(rotationRunnable, delayMs)
        }
    }

    private fun rotateToNextAd() {
        if (isDestroyed || !isStarted) return

        val nextAd = if (loadedAds.isEmpty()) null else loadedAds.removeFirst()

        if (nextAd == null) {
            waitingForBufferedAd = true
            if (idleStartedAt == 0L && isStarted) {
                idleStartedAt = SystemClock.elapsedRealtime()
            }
            Log.d(TAG, "Buffer empty. Waiting for the next loaded banner.")
            fillBuffer()
            return
        }

        waitingForBufferedAd = false

        val previousAd = currentAd
        val previousSourceId = previousAd?.let { getAdSourceId(it) }
        val previousRefresh = previousSourceId?.let { sourceRefreshTimes[it] }
            ?: defaultRefreshTime

        val currentIdleMs = if (idleStartedAt > 0L) {
            (SystemClock.elapsedRealtime() - idleStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        val idleSeconds = ((idleAccumulatedMs + currentIdleMs) / 1000L).toInt()
        idleStartedAt = 0L
        idleAccumulatedMs = 0L

        if (previousAd != null) {
            previousRefreshTimeSeconds = previousRefresh
            previousIdleTimeSeconds = idleSeconds
            previousAdSourceId = previousSourceId
        }

        mainHandler.removeCallbacks(rotationRunnable)
        rotationDeadline = 0L
        remainingRefreshTimeMs = 0L

        displayAd(nextAd)

        previousAd?.destroy()

        Log.d(
            TAG,
            "Banner rotated. Remaining cache: ${loadedAds.size}, idle: ${idleSeconds}s"
        )

        fillBuffer()
    }

    private fun displayNextAd() {
        if (isDestroyed || currentAd != null || loadedAds.isEmpty()) return

        val ad = loadedAds.removeFirst()
        displayAd(ad)
        fillBuffer()
    }

    private fun displayAd(ad: BannerAd) {
        if (activity.isFinishing || activity.isDestroyed) {
            ad.destroy()
            return
        }

        currentAdView?.unregisterBannerAd()
        bannerContainer.removeAllViews()

        val adView = AdView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        bannerContainer.addView(adView)
        adView.registerBannerAd(ad, activity)

        currentAd = ad
        currentAdView = adView
        bannerContainer.visibility = View.VISIBLE
        bannerContainer.requestLayout()
    }

    private fun scheduleRetry() {
        if (isDestroyed) return

        val retryDelay = currentBackoffSeconds
        currentBackoffSeconds = (currentBackoffSeconds * 2)
            .coerceAtMost(bufferSize * defaultRefreshTime)

        scheduledRetries++
        mainHandler.postDelayed(
            {
                scheduledRetries = (scheduledRetries - 1).coerceAtLeast(0)
                if (!isDestroyed) {
                    loadOneAd()
                }
            },
            retryDelay * 1000L
        )
    }

    private var currentBackoffSeconds = 1

    private fun getAdSourceId(ad: BannerAd): String? {
        return runCatching {
            ad.getResponseInfo().loadedAdSourceResponseInfo?.id
        }.getOrNull()
    }

    private fun isBannerVisible(): Boolean {
        return currentAdView?.let { view ->
            view.isShown && view.width > 0 && view.height > 0
        } == true
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
