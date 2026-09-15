package com.admobads.ads

import android.app.Activity
import android.app.Application
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
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.material.card.MaterialCardView

class AdmobAppOpenAd(
    applicationContext: Application,
    private val ad_Id: String,
    private val exceptionalActivities: List<String> = emptyList()
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {
    private var appOpenAd: AppOpenAd? = null
    private var loadCallback: AdLoadCallback<AppOpenAd>? = null
    private var currentActivity: Activity? = null
    private var TAG = "AppOpenManager"
    private var isLoadingAd = false
    private var isColdStart = true
    private var blockedActivity: Activity? = null
    var loadingView: View? = null
    private var currentComposeLoadingView: View? = null
    private var backCallback: androidx.activity.OnBackPressedCallback? = null
    private var adMessage = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStart(owner: LifecycleOwner) {
        try {

            Log.d(
                TAG,
                "onStart: Coming Inside ${GlobalState.isInterShowing} and ${shouldShowAppOpenAd()}"
            )

            if (isColdStart) {
                isColdStart = false
                return
            }

            if (shouldShowAppOpenAd()) {
                currentActivity?.let {
                    if (isNetworkAvailable(it) && !GlobalState.isInterShowing && shouldshowAppOpen) {
                        loadAd()
                    }
                }

            }

        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
    }


    fun loadAd() {

        if (!AdmobSdkGuard.ensureInitialized("AdmobAppOpenAd.loadAd")) {
            isShowingAd = false
            isLoadingAd = false
            return
        }

        if (isLoadingAd || isAppOpenAdAvailable() || isShowingAd) {
            return
        }

        if (isPurchased) {
            return
        }

        currentActivity?.let {
            blockTouches(it)
        }
        isShowingAd = true
        loadingView = currentActivity?.showAdLoadingView()

        isLoadingAd = true

        loadCallback =
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    this@AdmobAppOpenAd.appOpenAd = ad
                    Log.d(LOG_TAG, "App Open Ad Loaded")
                    isLoadingAd = false
                    adMessage = "App Open Loaded"
                    runOnMainThread {
                        currentActivity?.hideAdLoadingView(loadingView)
                        showAdIfAvailable()
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("error", loadAdError.message)
                    isLoadingAd = false
                    adMessage = "App Open Loading Failed Error : ${loadAdError.message}"
                    Log.d(LOG_TAG, "App Open Ad Failed")
                    runOnMainThread {
                        unblockTouches()
                        isShowingAd = false
                        currentActivity?.hideAdLoadingView(loadingView)
                    }
                }
            }

        try {
            loadCallback?.let { callback ->
                AppOpenAd.load(adRequest, callback)
            }
        } catch (e: Exception) {
            isShowingAd = false
            isLoadingAd = false
            unblockTouches()
            e.printStackTrace()
        }


    }


    private fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null
    }

    fun showAdIfAvailable(
    ) {

        Log.d(LOG_TAG, "Load $isPurchased , $isShowingAd")

        if (isPurchased) {
            return
        }

        val ad = appOpenAd ?: run {
            isShowingAd = false
            unblockTouches()
            return
        }

        Log.d(LOG_TAG, "Will show ad.")
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdPaid(value: AdValue) {
                AdRevenueTracker.handlePaid(
                    adValue = value,
                    adUnitId = ad_Id,
                    adFormat = AdRevenueTracker.FORMAT_APP_OPEN,
                    logTag = LOG_TAG
                )
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                runOnMainThread {
                    isShowingAd = false
                    unblockTouches()
                }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                appOpenAd = null
                runOnMainThread {
                    isShowingAd = false
                    unblockTouches()
                }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(LOG_TAG, "onAdShowedFullScreenContent.")
            }
        }
        isShowingAd = true
        runOnMainThread {
            currentActivity?.let {
                if (it.hasWindowFocus()) {
                    ad.show(it)
                } else {
                    appOpenAd = null
                    isShowingAd = false
                    unblockTouches()
                }
            } ?: run {
                appOpenAd = null
                isShowingAd = false
                unblockTouches()
            }
        }
    }

    private val adRequest: AdRequest
        get() {
            return AdRequest.Builder(ad_Id).build()
        }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {

        try {
            currentActivity?.hideAdLoadingView(loadingView)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        currentActivity = null

    }

    companion object {
        private const val LOG_TAG = "AppOpenManager"
        var isShowingAd = false
        private var isPurchased = false
        private var isComposed = false
        private var shouldshowAppOpen = true

        private var dialogTextColor = Color.BLACK
        private var dialogBackgroundColor = "#F8F8F8".toColorInt()
        fun setPurchased(isPurchase: Boolean = false) {
            isPurchased = isPurchase
        }

        fun setComposed(isCompose: Boolean = false) {
            isComposed = isCompose
        }

        fun shouldshowAppOpen(isInterstitialShowing: Boolean = true) {
            shouldshowAppOpen = isInterstitialShowing
        }

        fun setDialogTextColor(textcolor: Int) {
            this.dialogTextColor = textcolor
        }

        fun setDialogBGColor(bgcolor: Int) {
            this.dialogBackgroundColor = bgcolor
        }
    }

    /**
     * Constructor
     */
    init {
        applicationContext.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }


    fun Activity.showAdLoadingView(): View {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val loadingView =
            layoutInflater.inflate(R.layout.tlib_ad_loading_dialog, rootView, false)

        loadingView.findViewById<TextView>(R.id.textView21).setTextColor(dialogTextColor)
        loadingView.findViewById<ProgressBar>(R.id.progressBar).indeterminateTintList =
            ColorStateList.valueOf(dialogTextColor)

        loadingView.findViewById<TextView>(R.id.textView21).text =
            getString(R.string.tlib_loading)
        loadingView.findViewById<MaterialCardView>(R.id.dialogBg)
            .setCardBackgroundColor(dialogBackgroundColor)

        rootView.addView(loadingView)
        return loadingView
    }

    fun Activity.hideAdLoadingView(loadingView: View?) {
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


    private fun shouldShowAppOpenAd(): Boolean {
        val activityName = currentActivity?.javaClass?.simpleName ?: return false

        return shouldshowAppOpen &&
                !activityName.contains("splash", ignoreCase = true) &&
                !activityName.contains("iap", ignoreCase = true) &&
                !activityName.contains("AdActivity", ignoreCase = true) &&
                !activityName.contains("premium", ignoreCase = true) &&
                !activityName.contains("subscription", ignoreCase = true) &&
                !exceptionalActivities.contains(activityName)

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
            mainHandler.post(action)
        }
    }

}