package com.admobads.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
import com.admobads.ads.utils.isNetworkAvailable
import com.admobads.data.InterAdModel
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class AdmobInterstitialAd private constructor() {

    private var splashInterstitialAd: InterstitialAd? = null
    private var splashInterAdUnitId: String = ""
    private var TAG = "AdmobInterstitialAd_"
    private var mInterstitialAd: InterstitialAd? = null
    private var isPreviousAdLoading = false
    private var isPurchased = false
    private var isComposed = false
    private var currentComposeLoadingView: View? = null
    private var inter_counter_start = 2
    private var inter_counter_start_inside = 2
    private var inter_counter_gap = 3
    private var inter_counter_gap_inside = 3
    private var inside_inter_ad_id = ""
    private var inter_type = "click"
    private var inter_counter_start_time = 0L
    private var load_inter_counter_start_before = 0L
    private var inter_counter_gap_time = 0L
    private var load_inter_counter_gap_before = 0L
    private var isFirstTimeInterShown = false
    private var lastInterShownTime = 0L
    private var appStartTime = 0L
    private var interTimerResetAfterAppOpen = false
    private var hasShownInsideInter = false
    private var clicksBetweenInterTimerStart = 0
    private var clicksBetweenInterTimer = 0
    private var timerReadyClickCount = 0
    private var foregroundActivityClass: String? = null
    private var timerReadyActivityClass: String? = null
    private var timerReadyRunnable: Runnable? = null
    private var clickedDuringTimer = false
    private var clickGapEnabled = false
    private var activityWatchRegistered = false
    private var appContext: Context? = null
    private var timeLoadRunnable: Runnable? = null
    private var isTimeAdLoaded = false
    private var shouldLoadAd = false
    private var dialogBackgroundColor = "#F8F8F8".toColorInt()
    private var dialogTextColor = Color.BLACK
    var blockedActivity: Activity? = null
    private var isInterIntialized = false
    private var loadingtype = "manual"
    private var backCallback: androidx.activity.OnBackPressedCallback? = null
    private var adMessage: String = "Ad Loading"
    private var isAppInForeground = true
    private var pendingActivity: Activity? = null
    private var pendingLoadingView: View? = null
    private var splashAdPresented = false
    private var pendingSplashCallBack: ((Boolean) -> Unit)? = null
    private var interCallback: InterstitialAdEventCallback? = null
    private var shouldshowAd = false


    companion object {
        @Volatile
        private var INSTANCE: AdmobInterstitialAd? = null

        fun getInstance(): AdmobInterstitialAd {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobInterstitialAd().also { INSTANCE = it }
            }
        }
    }


    init {

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                resumeAdIfNeeded()
                Log.d("Activityisresumed", "Resumed")
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
        if (splashAdPresented) return
        if (pendingActivity != null && (mInterstitialAd != null || splashInterstitialAd != null) && shouldshowAd) {
            pendingActivity?.let { activity ->

                val loadingView = pendingLoadingView
                var resumeRetries = 0

                adRunnable?.let { handler.removeCallbacks(it) }
                adRunnable = Runnable {
                    if (splashAdPresented || activity.isFinishing || activity.isDestroyed) return@Runnable
                    if (!activity.hasWindowFocus()) {
                        if (resumeRetries < 8) {
                            resumeRetries++
                            adRunnable?.let { handler.postDelayed(it, 300) }
                            return@Runnable
                        }
                        pendingSplashCallBack?.let { callback ->
                            finishSplashAdAttempt(activity, loadingView, callback, shown = false)
                        }
                        return@Runnable
                    }
                    splashAdPresented = true
                    pendingSplashCallBack = null
                    pendingActivity = null
                    pendingLoadingView = null
                    activity.hideAdLoadingView(loadingView)
                    if (splashInterstitialAd != null) {
                        splashInterstitialAd?.adEventCallback = interCallback
                        splashInterstitialAd?.show(activity)
                    } else {
                        mInterstitialAd?.adEventCallback = interCallback
                        mInterstitialAd?.show(activity)
                    }

                }
                adRunnable?.let { handler.postDelayed(it, 1000) }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var adRunnable: Runnable? = null

    fun setPurchased(isPurchased: Boolean = false) {
        this.isPurchased = isPurchased
    }

    fun isPurchased(): Boolean = isPurchased

    fun setComposed(isComposed: Boolean = false) {
        this.isComposed = isComposed
    }

    fun isComposed(): Boolean = isComposed

    fun setLoadingDialogBgColor(loadingDialogBgColor: Int) {
        this.dialogBackgroundColor = loadingDialogBgColor
        AdmobAppOpenAd.setDialogBGColor(loadingDialogBgColor)
        AdmobRewardedAd.getInstance().setLoadingDialogBgColor(loadingDialogBgColor)
        AdmobPreloadInterstitialAd.getInstance().setLoadingDialogBgColor(loadingDialogBgColor)
    }

    fun setLoadingDialogTextColor(loadingDialogTextColor: Int) {
        this.dialogTextColor = loadingDialogTextColor
        AdmobAppOpenAd.setDialogTextColor(loadingDialogTextColor)
        AdmobPreloadInterstitialAd.getInstance().setLoadingDialogTextColor(loadingDialogTextColor)
        AdmobRewardedAd.getInstance().setLoadingDialogTextColor(loadingDialogTextColor)
    }

    fun initInterFromConfig(
        context: Context,
        config: InterAdModel,
        inside_inter_ad_id: String
    ) {

        if (isInterIntialized) {
            return
        }

        isInterIntialized = true
        interTimerResetAfterAppOpen = config.inter_timer_reset_after_app_open
        clicksBetweenInterTimerStart = config.clicks_between_inter_timer_start.coerceAtLeast(0)
        clicksBetweenInterTimer = config.clicks_between_inter_timer_gap.coerceAtLeast(0)
        appContext = context.applicationContext
        registerActivityWatch(context)


        Log.d(TAG, "Inside  ${config?.loading_type}")

        loadingtype = if (config.loading_type == "manual") "manual" else ""

        if (config.loading_type == "manual") {

            if (config.inter_type == "timer") {
                initTimeBased(
                    context = context,
                    interStartAfterSeconds = config.inter_start_after_seconds,
                    loadFirstBeforeSeconds = config.inter_start_load_before_seconds,
                    gapAfterSeconds = config.inter_gap_after_seconds,
                    loadGapBeforeSeconds = config.inter_gap_load_before_seconds,
                    inside_inter_ad_id = inside_inter_ad_id
                )
            } else {
                initValues(
                    context = context,
                    inter_counter_start = config.inter_counter_start,
                    inter_counter_gap = config.inter_counter_gap,
                    inside_inter_ad_id = inside_inter_ad_id
                )
            }

        } else {
            Log.d(TAG, "API onStart")
            AdmobPreloadInterstitialAd.getInstance().start(
                config,
                inside_inter_ad_id
            )
        }

    }

    private fun initializeSdk(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val appId = try {
                context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                    ?.getString("com.google.android.gms.ads.APPLICATION_ID")
                    .orEmpty()
            } catch (e: Exception) {
                ""
            }

            if (appId.isNotEmpty()) {
                MobileAds.initialize(
                    context.applicationContext,
                    InitializationConfig.Builder(appId).build()
                ) {

                }
            }
        }
    }

    private fun initTimeBased(
        context: Context,
        interStartAfterSeconds: Long,
        loadFirstBeforeSeconds: Long,
        gapAfterSeconds: Long,
        loadGapBeforeSeconds: Long,
        inside_inter_ad_id: String
    ) {
        inter_type = "timer"
        this.inside_inter_ad_id = inside_inter_ad_id

        inter_counter_start_time = interStartAfterSeconds * 1000
        load_inter_counter_start_before = loadFirstBeforeSeconds * 1000

        inter_counter_gap_time = gapAfterSeconds * 1000
        load_inter_counter_gap_before = loadGapBeforeSeconds * 1000

        if (inter_counter_start_time == 0L && inter_counter_gap_time == 0L) {
            shouldLoadAd = false
            return
        }

        if (inter_counter_start_time == 0L && inter_counter_gap_time > 0L) {
            inter_counter_start_time = inter_counter_gap_time
            load_inter_counter_start_before = load_inter_counter_gap_before
        }

        shouldLoadAd = true
        appStartTime = System.currentTimeMillis()
        isFirstTimeInterShown = false

        scheduleTimeBasedLoad(context)
    }

    private fun initValues(
        context: Context,
        inter_counter_start: Int = 2,
        inter_counter_gap: Int = 3,
        inside_inter_ad_id: String

    ) {
        this.inter_counter_start = inter_counter_start
        this.inter_counter_start_inside = inter_counter_start
        this.inter_counter_gap = inter_counter_gap
        this.inter_counter_gap_inside = inter_counter_gap
        this.inside_inter_ad_id = inside_inter_ad_id

        if (inter_counter_start == 0) {
            this.inter_counter_start = inter_counter_gap
        }

        if (inter_counter_start == 0 && inter_counter_gap == 0) {
            Log.d(TAG, "Both are 0")
            shouldLoadAd = false
        } else if (inter_counter_start != 0 && inter_counter_start <= 2) {
            Log.d(TAG, "inter_counter_start = ${inter_counter_start}")
            shouldLoadAd = true
            load(context, inside_inter_ad_id)
        } else {
            Log.d(TAG, "inter_counter_gap = ${inter_counter_gap}")
            shouldLoadAd = true
            if (inter_counter_gap != 0 && inter_counter_gap <= 2) {
                load(context, inside_inter_ad_id)
            }
        }

    }

    private fun scheduleTimeBasedLoad(context: Context) {

        if (!isFirstTimeInterShown) {
            appStartTime
        } else {
            lastInterShownTime
        }

        val showAfter = if (!isFirstTimeInterShown) {
            inter_counter_start_time
        } else {
            inter_counter_gap_time
        }

        val loadBefore = if (!isFirstTimeInterShown) {
            load_inter_counter_start_before
        } else {
            load_inter_counter_gap_before
        }

        val loadDelay = showAfter - loadBefore

        timeLoadRunnable?.let { handler.removeCallbacks(it) }

        if (loadDelay <= 0) {
            timeLoadRunnable = null
            load(context, inside_inter_ad_id)
            scheduleTimerReadyMark()
            return
        }

        timeLoadRunnable = Runnable {
            if (!isPurchased && mInterstitialAd == null) {
                load(context, inside_inter_ad_id)
                isTimeAdLoaded = true
            }
        }
        timeLoadRunnable?.let { handler.postDelayed(it, loadDelay) }
        scheduleTimerReadyMark()
    }

    private fun scheduleTimerReadyMark() {
        timerReadyRunnable?.let { handler.removeCallbacks(it) }
        timerReadyActivityClass = null
        timerReadyClickCount = 0
        clickedDuringTimer = false
        clickGapEnabled = false

        val baseTime = if (!isFirstTimeInterShown) appStartTime else lastInterShownTime
        val requiredTime = if (!isFirstTimeInterShown) {
            inter_counter_start_time
        } else {
            inter_counter_gap_time
        }
        val delay = (baseTime + requiredTime) - System.currentTimeMillis()
        lateinit var markReady: Runnable
        markReady = Runnable {
            if (!isTimeReadyToShow()) {
                val remaining = (baseTime + requiredTime) - System.currentTimeMillis()
                if (remaining > 0) {
                    handler.postDelayed(markReady, remaining)
                }
                return@Runnable
            }
            timerReadyActivityClass = foregroundActivityClass
            timerReadyClickCount = 0
            val clicksBetween = clicksRequiredAfterTimer()
            clickGapEnabled = clicksBetween > 0 && !clickedDuringTimer
            Log.d(
                TAG,
                "Timer completed on $timerReadyActivityClass, " +
                        "clickGapEnabled=$clickGapEnabled, clicksBetween=$clicksBetween"
            )
        }
        if (delay <= 0) {
            handler.post(markReady)
        } else {
            timerReadyRunnable = markReady
            handler.postDelayed(markReady, delay)
        }
    }

    fun resetAfterAppOpenShown() {
        if (!interTimerResetAfterAppOpen || !isInterIntialized) {
            return
        }

        if (loadingtype != "manual") {
            AdmobPreloadInterstitialAd.getInstance().resetCounterAndTimer()
            return
        }

        if (inter_type == "timer") {
            if (isFirstTimeInterShown) {
                lastInterShownTime = System.currentTimeMillis()
            } else {
                appStartTime = System.currentTimeMillis()
                lastInterShownTime = 0L
            }
            timeLoadRunnable?.let { handler.removeCallbacks(it) }
            timeLoadRunnable = null
            scheduleTimerReadyMark()
        } else {
            inter_counter_start = if (hasShownInsideInter && inter_counter_gap != 0) {
                inter_counter_gap
            } else if (inter_counter_start_inside != 0) {
                inter_counter_start_inside
            } else {
                inter_counter_gap
            }
        }

        Log.d(
            TAG,
            "Interstitial counter and timer reset after app open. counter=$inter_counter_start"
        )
    }

    private fun isTimeReadyToShow(): Boolean {

        if (isFirstTimeInterShown && inter_counter_gap_time <= 0) {
            return false
        }

        val baseTime = if (!isFirstTimeInterShown) {
            appStartTime
        } else {
            lastInterShownTime
        }

        val requiredTime = if (!isFirstTimeInterShown) {
            inter_counter_start_time
        } else {
            inter_counter_gap_time
        }

        val elapsed = System.currentTimeMillis() - baseTime
        return elapsed >= requiredTime

    }

    private fun registerActivityWatch(context: Context) {
        if (activityWatchRegistered) return
        val app = context.applicationContext as? Application ?: return
        activityWatchRegistered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity.packageName != app.packageName) return
                foregroundActivityClass = activity.javaClass.name
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    internal fun noteClickDuringTimer() {
        clickedDuringTimer = true
        clickGapEnabled = false
        Log.d(TAG, "Click while timer is running, click gap disabled")
    }

    internal fun enableClickGapAfterCleanTimer() {
        val clicksBetween = clicksRequiredAfterTimer()
        if (clicksBetween <= 0 || clickedDuringTimer || !isTimeReadyToShow()) {
            return
        }
        if (clickGapEnabled) return
        clickGapEnabled = true
        timerReadyClickCount = 0
        Log.d(
            TAG,
            "No click during timer, click gap enabled. clicksBetween=$clicksBetween"
        )
    }

    internal fun shouldHoldTimerForClicks(activity: Activity): Boolean {
        val clicksBetween = clicksRequiredAfterTimer()
        if (!clickGapEnabled || clicksBetween <= 0) return false
        timerReadyClickCount++
        Log.d(
            TAG,
            "Timer click $timerReadyClickCount/${clicksBetween + 1} on ${activity.javaClass.simpleName}"
        )
        return timerReadyClickCount <= clicksBetween
    }

    private fun clicksRequiredAfterTimer(): Int {
        val beforeFirstInterstitial = if (loadingtype == "manual") {
            !isFirstTimeInterShown
        } else {
            !AdmobPreloadInterstitialAd.getInstance().hasShownFirstInterstitial()
        }
        return if (beforeFirstInterstitial) {
            clicksBetweenInterTimerStart
        } else {
            clicksBetweenInterTimer
        }
    }

    internal fun resetTimerClickCount() {
        timerReadyClickCount = 0
    }

    internal fun clearTimerClickGate() {
        timerReadyRunnable?.let { handler.removeCallbacks(it) }
        timerReadyRunnable = null
        timerReadyActivityClass = null
        timerReadyClickCount = 0
        clickedDuringTimer = false
        clickGapEnabled = false
    }


    fun loadSplashInter(
        ctx: Activity, id: String,
        onAdLoaded: () -> Unit,
        onAdFailedToLoad: () -> Unit,
    ) {
        if (!AdmobSdkGuard.ensureInitialized("AdmobInterstitialAd.loadSplashInter")) {
            onAdFailedToLoad.invoke()
            return
        }

        if (!isNetworkAvailable(ctx)) {
            onAdFailedToLoad.invoke()
            return
        }

        if (splashInterstitialAd != null) {
            onAdLoaded.invoke()
            return
        }

        val callback = object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                adMessage = "Splash Ad Loaded"
                isPreviousAdLoading = false
                splashInterAdUnitId = id
                splashInterstitialAd = interstitialAd
                runOnMainThread { onAdLoaded.invoke() }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                adMessage = "Splash Ad Loading Failed Error: ${adError.message}"
                isPreviousAdLoading = false
                runOnMainThread { onAdFailedToLoad.invoke() }
            }
        }
        if (!isPreviousAdLoading) {
            isPreviousAdLoading = true
            InterstitialAd.load(AdRequest.Builder(id).build(), callback)
        }
    }


    fun showSplashInterAd(
        activity: Activity,
        message: (String) -> Unit = {},
        callBack: (Boolean) -> Unit
    ) {


        if (isPurchased) {
            message.invoke("Premium User")
            callBack.invoke(true)
            return
        }


        if (splashInterstitialAd == null) {
            callBack.invoke(false)
            message.invoke(adMessage)
            return
        }

        message.invoke(adMessage)

        val callback = object : InterstitialAdEventCallback {
            override fun onAdPaid(value: AdValue) {
                AdRevenueTracker.handlePaid(
                    adValue = value,
                    adUnitId = splashInterAdUnitId,
                    adFormat = AdRevenueTracker.FORMAT_INTERSTITIAL,
                    logTag = TAG
                )
            }

            override fun onAdDismissedFullScreenContent() {
                runOnMainThread {
                    callBack.invoke(true)
                    unblockTouches()
                }
                splashInterstitialAd = null
                shouldshowAd = false
                GlobalState.isInterShowing = false
                AdmobAppOpenAd.shouldshowAppOpen()
                adMessage = ""

            }

            override fun onAdShowedFullScreenContent() {
                splashInterstitialAd = null
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                shouldshowAd = false
                adMessage = ""
                GlobalState.isInterShowing = false
                AdmobAppOpenAd.shouldshowAppOpen()
                runOnMainThread {
                    callBack.invoke(false)
                    message.invoke("Splash Ad Failed to Show Error : ${fullScreenContentError.message}")
                    unblockTouches()
                }
            }
        }

        interCallback = callback
        AdmobAppOpenAd.shouldshowAppOpen(false)
        GlobalState.isInterShowing = true
        shouldshowAd = true
        splashAdPresented = false
        pendingSplashCallBack = callBack
        blockTouches(activity)
        val loadingView = activity.showAdLoadingView()
        pendingActivity = activity
        pendingLoadingView = loadingView
        var focusRetries = 0
        adRunnable?.let { handler.removeCallbacks(it) }
        adRunnable = Runnable {
            // The ad taking the screen makes this activity lose window focus.
            // That is not a failed show, so don't leave the premium screen.
            if (splashAdPresented) return@Runnable

            if (activity.isFinishing || activity.isDestroyed) {
                Log.d(TAG, "Splash ad skipped: activity gone")
                finishSplashAdAttempt(activity, loadingView, callBack, shown = false)
                return@Runnable
            }

            if (!isAppInForeground || !activity.hasWindowFocus()) {
                if (focusRetries < 8) {
                    focusRetries++
                    adRunnable?.let { handler.postDelayed(it, 300) }
                    return@Runnable
                }
                Log.d(TAG, "Splash ad skipped: invalid state")
                finishSplashAdAttempt(activity, loadingView, callBack, shown = false)
                return@Runnable
            }

            splashAdPresented = true
            pendingSplashCallBack = null
            pendingActivity = null
            pendingLoadingView = null
            activity.hideAdLoadingView(loadingView)
            splashInterstitialAd?.adEventCallback = callback
            splashInterstitialAd?.show(activity)
        }
        adRunnable?.let { handler.postDelayed(it, 1500) }
    }

    private fun finishSplashAdAttempt(
        activity: Activity,
        loadingView: View?,
        callBack: (Boolean) -> Unit,
        shown: Boolean
    ) {
        AdmobAppOpenAd.shouldshowAppOpen(true)
        activity.hideAdLoadingView(loadingView)
        shouldshowAd = false
        pendingActivity = null
        pendingLoadingView = null
        pendingSplashCallBack = null
        unblockTouches()
        GlobalState.isInterShowing = false
        callBack.invoke(shown)
    }

    private fun load(ctx: Context, id: String) {

        Log.d(TAG, "Loaded Requested $shouldLoadAd")

        if (!AdmobSdkGuard.ensureInitialized("AdmobInterstitialAd.load")) {
            return
        }

        if (isPurchased) {
            return
        }

        if (!shouldLoadAd) {
            return
        }

        if (!isNetworkAvailable(ctx)) {
            adMessage = "Internet not available"
            return
        }


        if (splashInterstitialAd != null) {
            mInterstitialAd = splashInterstitialAd
            splashInterstitialAd = null
            return
        }

        if (mInterstitialAd != null) {
            return
        }


        val callback = object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                isPreviousAdLoading = false
                mInterstitialAd = interstitialAd
                adMessage = "Inside Ad Loaded"
                Log.d(TAG, "AdmobInterstitialAd: onAdLoaded")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                isPreviousAdLoading = false
                adMessage = "Inside Ad Loading Failed Error : ${adError.message}"
                Log.d(TAG, "AdmobInterstitialAd: onAdFailedToLoad")
            }
        }
        if (!isPreviousAdLoading) {
            isPreviousAdLoading = true
            InterstitialAd.load(AdRequest.Builder(id).build(), callback)
        }
    }


    fun showInterAd(
        activity: Activity,
        message: (String) -> Unit = {},
        callBack: () -> Unit
    ) {

        if (AdmobAppOpenAd.isShowingAd) {
            message.invoke("AppOpen Ad is showing")
            return
        }

        if (isPurchased) {
            message.invoke("AppOpen Ad is showing")
            callBack.invoke()
            return
        }



        if (!AdmobSdkGuard.ensureInitialized("AdmobInterstitialAd.showInterAd")) {
            message.invoke("MobileAds SDK not initialized")
            callBack.invoke()
            return
        }

        if (loadingtype == "") {
            AdmobPreloadInterstitialAd.getInstance().showPreloadInter(
                activity,
                message = {
                    message.invoke(it)
                },
                callBack = {
                    callBack.invoke()
                }
            )
            return
        }

        if (inter_type == "timer") {


            activity.showTimeBasedInter(
                message = { message.invoke(it) },
                callBack = { callBack.invoke() }
            )

            return
        }

        message.invoke(adMessage)

        Log.d(TAG, "Counter == $inter_counter_start")


        if (inter_counter_start != 0 && mInterstitialAd == null) {
            callBack.invoke()
            inter_counter_start -= 1
            if (inter_counter_start <= 2) {
                load(activity, inside_inter_ad_id)
            }
            return
        } else if (inter_counter_start == 0) {
            if (mInterstitialAd == null) {
                callBack.invoke()
                return
            }
        } else {
            inter_counter_start -= 1
            if (inter_counter_start > 0) {
                callBack.invoke()
                return
            }
        }

        val callback = object : InterstitialAdEventCallback {
            override fun onAdPaid(value: AdValue) {
                AdRevenueTracker.handlePaid(
                    adValue = value,
                    adUnitId = inside_inter_ad_id,
                    adFormat = AdRevenueTracker.FORMAT_INTERSTITIAL,
                    logTag = TAG
                )
            }

            override fun onAdDismissedFullScreenContent() {
                GlobalState.isInterShowing = false
                adMessage = ""
                shouldshowAd = false
                AdmobAppOpenAd.shouldshowAppOpen()
                runOnMainThread {
                    callBack.invoke()
                    unblockTouches()
                    if (inter_counter_start != 0 && inter_counter_start <= 2) {
                        load(activity, inside_inter_ad_id)
                    }
                }
            }

            override fun onAdShowedFullScreenContent() {
                mInterstitialAd = null
                hasShownInsideInter = true
                inter_counter_start = inter_counter_gap
                runOnMainThread {
                    unblockTouches()
                }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                shouldshowAd = false
                adMessage = ""
                GlobalState.isInterShowing = false
                AdmobAppOpenAd.shouldshowAppOpen()
                runOnMainThread {
                    callBack.invoke()
                    message.invoke("Inside Ad Failed to Show Error : ${fullScreenContentError.message}")
                    unblockTouches()
                }
            }
        }
        interCallback = callback
        shouldshowAd = true
        GlobalState.isInterShowing = true
        blockTouches(activity)
        AdmobAppOpenAd.shouldshowAppOpen(false)
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
                unblockTouches()
                callBack.invoke()
                shouldshowAd = false
                AdmobAppOpenAd.shouldshowAppOpen(true)
                GlobalState.isInterShowing = false
                return@Runnable
            }

            mInterstitialAd?.adEventCallback = callback
            activity.hideAdLoadingView(loadingView)
            mInterstitialAd?.show(activity)
        }

        adRunnable?.let { handler.postDelayed(it, 1500) }
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

                if (isComposed) {
                    currentComposeLoadingView = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding loading view: ${e.message}")
        }
    }

    private fun Activity.showTimeBasedInter(
        message: (String) -> Unit = {},
        callBack: () -> Unit,
    ) {

        if (isPurchased) {
            message.invoke("Premium User")
            callBack.invoke()
            return
        }

        if (!isTimeReadyToShow()) {
            noteClickDuringTimer()
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        enableClickGapAfterCleanTimer()

        if (mInterstitialAd == null) {
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        if (shouldHoldTimerForClicks(this)) {
            message.invoke(adMessage)
            callBack.invoke()
            return
        }

        resetTimerClickCount()
        timerReadyActivityClass = null
        message.invoke(adMessage)

        val callback =
            object : InterstitialAdEventCallback {

                override fun onAdPaid(value: AdValue) {
                    AdRevenueTracker.handlePaid(
                        adValue = value,
                        adUnitId = inside_inter_ad_id,
                        adFormat = AdRevenueTracker.FORMAT_INTERSTITIAL,
                        logTag = TAG
                    )
                }

                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    lastInterShownTime = System.currentTimeMillis()
                    isFirstTimeInterShown = true
                    isTimeAdLoaded = false
                    GlobalState.isInterShowing = false
                    adMessage = ""
                    shouldshowAd = false
                    AdmobAppOpenAd.shouldshowAppOpen()
                    runOnMainThread {
                        callBack.invoke()
                        scheduleTimeBasedLoad(this@showTimeBasedInter)
                        unblockTouches()
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    mInterstitialAd = null
                }

                override fun onAdFailedToShowFullScreenContent(
                    fullScreenContentError: FullScreenContentError
                ) {
                    shouldshowAd = false
                    adMessage = ""
                    GlobalState.isInterShowing = false
                    Log.d("isInterstitialAdShowing", "Failed to show")
                    AdmobAppOpenAd.shouldshowAppOpen()
                    runOnMainThread {
                        callBack.invoke()
                        message.invoke("Inside Ad Failed to Show Error : ${fullScreenContentError.message}")
                        unblockTouches()
                    }
                }
            }

        interCallback = callback

        shouldshowAd = true
        GlobalState.isInterShowing = true
        blockTouches(this)
        AdmobAppOpenAd.shouldshowAppOpen(false)
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
                unblockTouches()
                callBack.invoke()
                shouldshowAd = false
                AdmobAppOpenAd.shouldshowAppOpen(true)
                GlobalState.isInterShowing = false
                return@Runnable
            }
            hideAdLoadingView(loadingView)
            mInterstitialAd?.adEventCallback = callback
            mInterstitialAd?.show(this)
            AdmobAppOpenAd.shouldshowAppOpen(false)

        }
        adRunnable?.let { handler.postDelayed(it, 1500) }
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
            CoroutineScope(Dispatchers.Main).launch {

                blockedActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

                backCallback?.remove()
                backCallback = null

                blockedActivity = null
            }
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
        AdmobPreloadInterstitialAd.getInstance().destroy()
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
        splashInterstitialAd = null

        interCallback = null
        shouldshowAd = false
        shouldLoadAd = false
        isPreviousAdLoading = false
        isTimeAdLoaded = false
        isFirstTimeInterShown = false

        appStartTime = 0L
        lastInterShownTime = 0L
        inter_counter_start_time = 0L
        load_inter_counter_start_before = 0L
        inter_counter_gap_time = 0L
        load_inter_counter_gap_before = 0L

        isInterIntialized = false

        GlobalState.isInterShowing = false
    }


}