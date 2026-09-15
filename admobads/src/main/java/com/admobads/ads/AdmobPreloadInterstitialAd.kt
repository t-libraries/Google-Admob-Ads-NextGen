package com.admobads.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.admobads.ads.utils.AdmobSdkGuard
import com.admobads.ads.utils.GlobalState
import com.admobads.data.InterAdModel
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.material.card.MaterialCardView

@SuppressLint("StaticFieldLeak")
class AdmobPreloadInterstitialAd private constructor() {
    private var AD_UNIT_ID = ""
    private var preloadedAdsCount = 0
    private var appStartTime = 0L
    private var TAG = "AdmobInterstitialAd_"
    private var interCounterStart = 2
    private var interCounterGap = 3
    private var currentCounter = interCounterStart
    private var shouldLoadAd = false
    private var isFirstTimeInterShown = false
    private var lastInterShownTime = 0L
    private var inter_counter_start_time = 0L
    private var inter_counter_gap_time = 0L
    private var inter_type = "click"
    private var backCallback: androidx.activity.OnBackPressedCallback? = null
    private var dialogBackgroundColor = "#F8F8F8".toColorInt()
    private var dialogTextColor = Color.BLACK
    private var currentComposeLoadingView: View? = null
    private var blockedActivity: Activity? = null
    private val handler = Handler(Looper.getMainLooper())
    private var adRunnable: Runnable? = null
    private var interCallback: InterstitialAdEventCallback? = null

    private var shouldshowAd = false

    private var isAppInForeground = true

    private var pendingActivity: Activity? = null
    private var pendingLoadingView: View? = null
    private var adMessage: String = "Ad Loading"

    private var mInterstitialAd: InterstitialAd? = null

    companion object {
        @Volatile
        private var INSTANCE: AdmobPreloadInterstitialAd? = null

        fun getInstance(): AdmobPreloadInterstitialAd {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobPreloadInterstitialAd().also { INSTANCE = it }
            }
        }
    }


    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                resumeAdIfNeeded()
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                pauseAd()
            }
        })
    }

    private fun pauseAd() {
        adRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun resumeAdIfNeeded() {
        if (pendingActivity != null && mInterstitialAd != null && shouldshowAd) {
            pendingActivity?.let { activity ->

                val loadingView = pendingLoadingView

                adRunnable = Runnable {
                    activity.hideAdLoadingView(loadingView)
                    mInterstitialAd?.adEventCallback = interCallback
                    mInterstitialAd?.show(activity)
                }
                adRunnable?.let { handler.postDelayed(it, 1000) }
            }

            pendingActivity = null
            pendingLoadingView = null
        }
    }


    fun setLoadingDialogBgColor(loadingDialogBgColor: Int) {
        this.dialogBackgroundColor = loadingDialogBgColor
        AdmobAppOpenAd.setDialogBGColor(loadingDialogBgColor)
    }

    fun setLoadingDialogTextColor(loadingDialogTextColor: Int) {
        this.dialogTextColor = loadingDialogTextColor
        AdmobAppOpenAd.setDialogTextColor(loadingDialogTextColor)
    }

    fun start(interAdModel: InterAdModel, adunitID: String) {

        if (!AdmobSdkGuard.ensureInitialized("AdmobPreloadInterstitialAd.start")) {
            adMessage = "MobileAds SDK not initialized"
            return
        }

        inter_type = interAdModel.inter_type

        if (interAdModel.inter_type == "timer") {
            appStartTime = System.currentTimeMillis()
            isFirstTimeInterShown = false
            lastInterShownTime = 0L
            shouldLoadAd =
                !(interAdModel.inter_start_after_seconds == 0L && interAdModel.inter_gap_after_seconds == 0L)
            if (!shouldLoadAd) {
                adMessage = "Both Inside Counters are 0"
                Log.d(TAG, "Ad loading disabled: both counters are 0")
                return
            }

            inter_counter_start_time =
                interAdModel.inter_start_after_seconds * 1000

            inter_counter_gap_time =
                interAdModel.inter_gap_after_seconds * 1000

        } else {
            shouldLoadAd = !(interCounterStart == 0 && interCounterGap == 0)
            if (!shouldLoadAd) {
                Log.d(TAG, "Ad loading disabled: both counters are 0")
                adMessage = "Both Inside Counters are 0"
                return
            }

            this.interCounterStart = interAdModel.inter_counter_start
            this.interCounterGap = interAdModel.inter_counter_gap

            if (interCounterStart != 0) {
                currentCounter = interCounterStart
            } else {
                if (interCounterGap != 0) {
                    currentCounter = interCounterGap
                } else {
                    currentCounter = 0
                }
            }
        }


        AD_UNIT_ID = adunitID

        if (AdmobInterstitialAd.getInstance().isPurchased()) {
            Log.d(TAG, "Inside Purchased")
            adMessage = "Premium User"
            return
        }

        Log.d(TAG, "Inside  ${interAdModel}")
        Log.d(TAG, "Inside ID  ${interAdModel.inter_type}")
        Log.d(TAG, "Inside ID  $AD_UNIT_ID")

        val adRequest = AdRequest.Builder(AD_UNIT_ID).build()
        val config = PreloadConfiguration(adRequest, bufferSize = 2)

        Log.d(TAG, "Inside Config  $config")

        InterstitialAdPreloader.start(
            AD_UNIT_ID,
            config,
            object : PreloadCallback {
                override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                    preloadedAdsCount++
                    adMessage = "Inside Ad Loaded"
                    Log.d(TAG, "Ad Ready | Total preloaded: $preloadedAdsCount")
                }

                override fun onAdsExhausted(preloadId: String) {
                    preloadedAdsCount = 0
                    Log.d(TAG, "All ads exhausted | Reloading")
                }

                override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                    Log.e(TAG, "Preload failed: ${adError.message}")
                    adMessage = "Inside Ad Loading Failed Error : ${adError.message}"
                }
            }
        )
    }

    fun showPreloadInter(
        activity: Activity,
        message: (String) -> Unit = {},
        callBack: () -> Unit,
    ) {

        if (AdmobInterstitialAd.getInstance().isPurchased()) {
            callBack.invoke()
            return
        }

        if (!isReady()) {
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        if (!shouldLoadAd) {
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        if (inter_type == "timer") {

            activity.showPreloadTimeInter(

                message = {
                    message.invoke(it)
                },
                callBack = {
                    callBack.invoke()
                }

            )
            return
        }

        message.invoke(adMessage)


        Log.d("TAG", "Counter is: $currentCounter")

        if (currentCounter > 1 || currentCounter == 0) {
            if (currentCounter != 0)
                currentCounter--
            Log.d(TAG, "Skipping ad | Counter: $currentCounter")
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        if (!isReady()) {
            callBack.invoke()
            return
        }

        lastInterShownTime = System.currentTimeMillis()
        isFirstTimeInterShown = true

        val ad = InterstitialAdPreloader.pollAd(AD_UNIT_ID)
        ad ?: run {
            callBack.invoke()
            return
        }

        mInterstitialAd = ad

        preloadedAdsCount--
        Log.d(TAG, "Ad shown | Remaining preloaded: $preloadedAdsCount")

        val callback = object : InterstitialAdEventCallback {
            override fun onAdPaid(value: AdValue) {
                AdRevenueTracker.handlePaid(
                    adValue = value,
                    adUnitId = AD_UNIT_ID,
                    adFormat = AdRevenueTracker.FORMAT_INTERSTITIAL,
                    logTag = TAG
                )
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad Showed")
                currentCounter = interCounterGap
                mInterstitialAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                GlobalState.isInterShowing = false
                Log.d(TAG, "Ad Dismissed")
                adMessage = ""
                mInterstitialAd = null
                shouldshowAd = false
                AdmobAppOpenAd.shouldshowAppOpen()
                runOnMainThread {
                    unblockTouches()
                    callBack.invoke()
                }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                GlobalState.isInterShowing = false
                Log.e(TAG, "Show Failed: ${fullScreenContentError.message}")
                AdmobAppOpenAd.shouldshowAppOpen()
                adMessage = ""
                shouldshowAd = false
                runOnMainThread {
                    unblockTouches()
                    message.invoke("Inside Ad Failed to Show Error : ${fullScreenContentError.message}")
                    callBack.invoke()
                }
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad Impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Ad Clicked")
            }
        }

        interCallback = callback
        shouldshowAd = true
        GlobalState.isInterShowing = true
        AdmobAppOpenAd.shouldshowAppOpen(false)
        blockTouches(activity)
        val loadingView = activity.showAdLoadingView()
        pendingActivity = activity
        pendingLoadingView = loadingView
        adRunnable = Runnable {
            if (!isAppInForeground ||
                activity.isFinishing ||
                activity.isDestroyed ||
                !activity.hasWindowFocus()
            ) {
                Log.d(TAG, "Ad skipped: app in background or activity invalid")
                activity.hideAdLoadingView(loadingView)
                shouldshowAd = false
                unblockTouches()
                AdmobAppOpenAd.shouldshowAppOpen(true)
                callBack.invoke()
                GlobalState.isInterShowing = false
                return@Runnable
            }
            ad.adEventCallback = callback
            ad.show(activity)
            activity.hideAdLoadingView(loadingView)
        }
        adRunnable?.let { handler.postDelayed(it, 1500) }
    }

    @SuppressLint("StaticFieldLeak")
    fun Activity.showPreloadTimeInter(
        message: (String) -> Unit = {},
        callBack: () -> Unit
    ) {

        if (AdmobInterstitialAd.getInstance().isPurchased()) {
            callBack.invoke()
            return
        }

        if (!shouldLoadAd) {
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        if (!isTimeReadyToShow()) {
            message.invoke(adMessage)
            callBack.invoke()
            return
        }


        val ad = InterstitialAdPreloader.pollAd(AD_UNIT_ID)
        ad ?: run {
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        message.invoke(adMessage)

        mInterstitialAd = ad

        val callback = object : InterstitialAdEventCallback {
            override fun onAdPaid(value: AdValue) {
                AdRevenueTracker.handlePaid(
                    adValue = value,
                    adUnitId = AD_UNIT_ID,
                    adFormat = AdRevenueTracker.FORMAT_INTERSTITIAL,
                    logTag = TAG
                )
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad Showed")
                mInterstitialAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad Dismissed")
                GlobalState.isInterShowing = false
                shouldshowAd = false
                mInterstitialAd = null
                adMessage = ""
                lastInterShownTime = System.currentTimeMillis()
                AdmobAppOpenAd.shouldshowAppOpen()
                isFirstTimeInterShown = true
                runOnMainThread {
                    unblockTouches()
                    callBack.invoke()
                }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                Log.e(TAG, "Show Failed: ${fullScreenContentError.message}")
                GlobalState.isInterShowing = false
                mInterstitialAd = null
                adMessage = ""
                lastInterShownTime = System.currentTimeMillis()
                AdmobAppOpenAd.shouldshowAppOpen()
                isFirstTimeInterShown = true
                shouldshowAd = false
                runOnMainThread {
                    unblockTouches()
                    message.invoke("Inside Ad Failed to Show Error : ${fullScreenContentError.message}")
                    callBack.invoke()
                }
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad Impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Ad Clicked")
            }
        }

        interCallback = callback
        shouldshowAd = true
        GlobalState.isInterShowing = true
        AdmobAppOpenAd.shouldshowAppOpen(false)
        blockTouches(this)
        val loadingView = showAdLoadingView()
        pendingActivity = this
        pendingLoadingView = loadingView
        adRunnable = Runnable {

            if (!isAppInForeground ||
                isFinishing ||
                isDestroyed ||
                !hasWindowFocus()
            ) {
                Log.d(TAG, "Ad skipped: app in background or activity invalid")
                hideAdLoadingView(loadingView)
                shouldshowAd = false
                unblockTouches()
                AdmobAppOpenAd.shouldshowAppOpen(true)
                callBack.invoke()
                GlobalState.isInterShowing = false
                return@Runnable
            }

            ad.adEventCallback = callback
            ad.show(this)
            hideAdLoadingView(loadingView)
            lastInterShownTime = System.currentTimeMillis()
            isFirstTimeInterShown = true

        }
        adRunnable?.let { handler.postDelayed(it, 1500) }

    }

    private fun isTimeReadyToShow(): Boolean {

        if (!shouldLoadAd) return false

        val now = System.currentTimeMillis()

        if (!isFirstTimeInterShown) {

            val requiredTime =
                if (inter_counter_start_time > 0)
                    inter_counter_start_time
                else
                    inter_counter_gap_time

            if (requiredTime <= 0) return false

            val elapsed = now - appStartTime

            Log.d(TAG, "First Ad elapsed=$elapsed required=$requiredTime")

            return elapsed >= requiredTime
        }

        if (inter_counter_gap_time <= 0) return false

        val elapsed = now - lastInterShownTime

        Log.d(TAG, "Gap elapsed=$elapsed required=$inter_counter_gap_time")

        return elapsed >= inter_counter_gap_time
    }

    fun isReady(): Boolean {
        return InterstitialAdPreloader.isAdAvailable(AD_UNIT_ID)
    }

    private fun Activity.showAdLoadingView(): View {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val loadingView =
            layoutInflater.inflate(R.layout.tlib_ad_loading_dialog, rootView, false)

        loadingView.findViewById<TextView>(R.id.textView21).setTextColor(dialogTextColor)
        loadingView.findViewById<ProgressBar>(R.id.progressBar).indeterminateTintList =
            ColorStateList.valueOf(dialogTextColor)
        loadingView.findViewById<MaterialCardView>(R.id.dialogBg)
            .setCardBackgroundColor(dialogBackgroundColor)

        rootView.addView(loadingView)
        return loadingView
    }

    private fun Activity.hideAdLoadingView(loadingView: View?) {
        try {
            if (!isFinishing && !isDestroyed) {
                val rootView = findViewById<ViewGroup>(android.R.id.content)
                loadingView?.let { view ->
                    if (view.parent != null) {
                        rootView.removeView(view)
                    }

                }

                if (AdmobInterstitialAd.getInstance().isComposed()) {
                    currentComposeLoadingView = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding loading view: ${e.message}")
        }
    }


    fun clearPreloadedAds() {
        InterstitialAdPreloader.destroy(AD_UNIT_ID)
        preloadedAdsCount = 0
        Log.d(TAG, "All preloaded ads cleared")
    }

    private fun blockTouches(activity: Activity) {
        try {
            blockedActivity = activity

            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )

            if (activity is androidx.activity.ComponentActivity) {
                backCallback = object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        Log.d(TAG, "backpressed")
                    }
                }
                activity.onBackPressedDispatcher.addCallback(activity, backCallback!!)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unblockTouches() {
        try {
            blockedActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

            backCallback?.remove()
            backCallback = null

            blockedActivity = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        adRunnable = null

        pendingActivity?.let { activity ->
            pendingLoadingView?.let { loadingView ->
                activity.hideAdLoadingView(loadingView)
            }
        }

        currentComposeLoadingView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)

        }
        currentComposeLoadingView = null

        blockedActivity?.let { activity ->
            try {
                activity.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing blocked activity flags: ${e.message}")
            }
        }

        backCallback?.remove()
        backCallback = null
        blockedActivity = null

        pendingActivity = null
        pendingLoadingView = null


        mInterstitialAd = null

        interCallback = null
        shouldshowAd = false
        shouldLoadAd = false
        isFirstTimeInterShown = false

        appStartTime = 0L
        lastInterShownTime = 0L
        inter_counter_start_time = 0L
        inter_counter_gap_time = 0L
        GlobalState.isInterShowing = false
    }

}