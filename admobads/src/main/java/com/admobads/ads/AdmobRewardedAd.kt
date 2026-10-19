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
import com.admobads.ads.utils.isNetworkAvailable
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@SuppressLint("StaticFieldLeak")
class AdmobRewardedAd private constructor() {

    private val TAG = "AdmobRewardedAd_"
    private val handler = Handler(Looper.getMainLooper())

    private var rewardedAd: RewardedAd? = null
    private var rewardAdUnitId: String = ""
    private var isRequestInProgress = false
    private var isPurchased = false
    private var rewardEarned = false
    private var resultDelivered = false
    private var shouldShowAd = false
    private var isAppInForeground = true

    private var dialogBackgroundColor = "#F8F8F8".toColorInt()
    private var dialogTextColor = Color.BLACK

    private var pendingActivity: Activity? = null
    private var pendingLoadingView: View? = null
    private var messageCallback: ((String) -> Unit)? = null
    private var resultCallback: ((Boolean) -> Unit)? = null
    private var adEventCallback: RewardedAdEventCallback? = null
    private var showRetry: Runnable? = null
    private var isPresenting = false

    private var blockedActivity: Activity? = null
    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    companion object {
        const val AD_NOT_AVAILABLE = "Ad is not available"
        const val REWARD_NOT_EARNED = "Reward is not earned"

        @Volatile
        private var INSTANCE: AdmobRewardedAd? = null

        fun getInstance(): AdmobRewardedAd {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobRewardedAd().also { INSTANCE = it }
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
            }
        })
    }

    fun setPurchased(isPurchased: Boolean = false) {
        this.isPurchased = isPurchased
    }

    fun isPurchased(): Boolean = isPurchased

    fun setLoadingDialogBgColor(loadingDialogBgColor: Int) {
        dialogBackgroundColor = loadingDialogBgColor
    }

    fun setLoadingDialogTextColor(loadingDialogTextColor: Int) {
        dialogTextColor = loadingDialogTextColor
    }


    fun showRewardedAd(
        activity: Activity,
        rewardAdId: String,
        message: (String) -> Unit = {},
        callBack: (isRewardEarned: Boolean) -> Unit
    ) {
        if (isPurchased) {
            callBack.invoke(true)
            return
        }

        if (isRequestInProgress) {
            message.invoke(AD_NOT_AVAILABLE)
            callBack.invoke(false)
            return
        }

        if (rewardAdId.isBlank()) {
            message.invoke(AD_NOT_AVAILABLE)
            callBack.invoke(false)
            return
        }

        if (AdmobAppOpenAd.isShowingAd) {
            message.invoke(AD_NOT_AVAILABLE)
            callBack.invoke(false)
            return
        }

        if (!AdmobSdkGuard.ensureInitialized("AdmobRewardedAd.showRewardedAd")) {
            message.invoke(AD_NOT_AVAILABLE)
            callBack.invoke(false)
            return
        }

        if (!isNetworkAvailable(activity)) {
            message.invoke(AD_NOT_AVAILABLE)
            callBack.invoke(false)
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            message.invoke(AD_NOT_AVAILABLE)
            callBack.invoke(false)
            return
        }

        isRequestInProgress = true
        resultDelivered = false
        rewardEarned = false
        shouldShowAd = true
        rewardAdUnitId = rewardAdId
        rewardedAd = null
        messageCallback = message
        resultCallback = callBack

        GlobalState.isInterShowing = true
        AdmobAppOpenAd.shouldshowAppOpen(false)
        blockTouches(activity)

        val loadingView = activity.showAdLoadingView()
        pendingActivity = activity
        pendingLoadingView = loadingView

        val loadCallback = object : AdLoadCallback<RewardedAd> {
            override fun onAdLoaded(ad: RewardedAd) {
                runOnMainThread {
                    if (resultDelivered) return@runOnMainThread
                    rewardedAd = ad
                    Log.d(TAG, "onAdLoaded")
                    showLoadedAd(activity, loadingView)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "onAdFailedToLoad: ${adError.message}")
                runOnMainThread {
                    activity.hideAdLoadingView(loadingView)
                    deliverResult(rewardEarned = false, adFailed = true)
                }
            }
        }

        RewardedAd.load(AdRequest.Builder(rewardAdId).build(), loadCallback)
    }

    private fun showLoadedAd(activity: Activity, loadingView: View?) {
        if (resultDelivered || isPresenting) return

        if (activity.isFinishing || activity.isDestroyed) {
            activity.hideAdLoadingView(loadingView)
            deliverResult(rewardEarned = false, adFailed = true)
            return
        }

        if (!isAppInForeground || !activity.hasWindowFocus()) {
            deferUntilReady(activity, loadingView)
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            activity.hideAdLoadingView(loadingView)
            deliverResult(rewardEarned = false, adFailed = true)
            return
        }

        val callback = object : RewardedAdEventCallback {
            override fun onAdPaid(value: AdValue) {

                AdRevenueTracker.handlePaid(
                    adValue = value,
                    adUnitId = rewardAdUnitId,
                    adFormat = AdRevenueTracker.FORMAT_REWARDED,
                    logTag = TAG
                )
            }

            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                runOnMainThread {
                    val earned = rewardEarned
                    deliverResult(
                        rewardEarned = earned,
                        adFailed = !earned,
                        failureMessage = REWARD_NOT_EARNED
                    )
                }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                Log.d(TAG, "onAdFailedToShow: ${fullScreenContentError.message}")
                runOnMainThread {
                    activity.hideAdLoadingView(loadingView)
                    deliverResult(rewardEarned = false, adFailed = true)
                }
            }
        }

        adEventCallback = callback
        ad.adEventCallback = callback
        isPresenting = true
        unblockTouches()
        activity.hideAdLoadingView(loadingView)
        pendingLoadingView = null
        pendingActivity = null

        ad.show(
            activity,
            OnUserEarnedRewardListener {
                rewardEarned = true
            }
        )
    }

    private fun deferUntilReady(
        activity: Activity,
        loadingView: View?,
        attempt: Int = 0
    ) {
        pendingActivity = activity
        pendingLoadingView = loadingView
        showRetry?.let { handler.removeCallbacks(it) }
        showRetry = Runnable {
            if (resultDelivered || isPresenting || rewardedAd == null) return@Runnable
            if (activity.isFinishing || activity.isDestroyed) {
                activity.hideAdLoadingView(loadingView)
                deliverResult(rewardEarned = false, adFailed = true)
                return@Runnable
            }
            if (!isAppInForeground) return@Runnable
            if (!activity.hasWindowFocus()) {
                if (attempt >= 5) {
                    activity.hideAdLoadingView(loadingView)
                    deliverResult(rewardEarned = false, adFailed = true)
                } else {
                    deferUntilReady(activity, loadingView, attempt + 1)
                }
                return@Runnable
            }
            showLoadedAd(activity, loadingView)
        }
        handler.postDelayed(showRetry!!, 400)
    }

    private fun resumeAdIfNeeded() {
        val activity = pendingActivity ?: return
        val loadingView = pendingLoadingView
        if (rewardedAd == null || !shouldShowAd || resultDelivered || isPresenting) return
        deferUntilReady(activity, loadingView)
    }

    private fun deliverResult(
        rewardEarned: Boolean,
        adFailed: Boolean,
        failureMessage: String = AD_NOT_AVAILABLE
    ) {
        if (resultDelivered) return
        resultDelivered = true
        isRequestInProgress = false
        isPresenting = false
        shouldShowAd = false
        this.rewardEarned = false
        rewardedAd = null
        showRetry?.let { handler.removeCallbacks(it) }
        showRetry = null

        val message = messageCallback
        val callBack = resultCallback
        messageCallback = null
        resultCallback = null
        adEventCallback = null
        pendingActivity = null
        pendingLoadingView = null

        GlobalState.isInterShowing = false
        AdmobAppOpenAd.shouldshowAppOpen()
        unblockTouches()

        if (rewardEarned) {
            callBack?.invoke(true)
        } else {
            if (adFailed) {
                message?.invoke(failureMessage)
            }
            callBack?.invoke(false)
        }
    }

    private fun Activity.showAdLoadingView(): View {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val loadingView = layoutInflater.inflate(R.layout.tlib_ad_loading_dialog, rootView, false)

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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding loading view: ${e.message}")
        }
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
}
