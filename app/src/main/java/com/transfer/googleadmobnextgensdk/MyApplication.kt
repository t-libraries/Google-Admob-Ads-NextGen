package com.transfer.googleadmobnextgensdk

import android.app.Application
import android.util.Log
import com.admobads.AdmobAdManger
import com.admobads.ads.AdmobInterstitialAd
import com.admobads.data.InterAdModel

class MyApplication : Application() {

    companion object {
        var myApplication: MyApplication? = null
    }

    val TAG = "myApplication"

    override fun onCreate() {
        super.onCreate()
        myApplication = this

        AdmobAdManger.setAdRevenueListener(1.30) { eventName, params ->
            Log.d("Fireasdfhaskdjflsadfj", "event name = $eventName and params are = $params")
        }

        AdmobAdManger.initializeSdk(this, getString(R.string.appid)) {
            Log.d("Fireasdfhaskdjflsadfj", "event name")
            AdmobInterstitialAd.getInstance().initInterFromConfig(
                this@MyApplication,
                InterAdModel(
                    inter_type = "click",
                    loading_type = "api",
                    inter_timer_reset_after_app_open = true,
                    clicks_between_inter_timer_start = 4,
                    clicks_between_inter_timer_gap = 0,
                    inter_counter_start = 1,
                    inter_counter_gap = 2,
                    inter_start_after_seconds = 10,
                    inter_start_load_before_seconds = 10,
                    inter_gap_after_seconds = 10,
                    inter_gap_load_before_seconds = 10
                ),
                "ca-app-pub-3940256099942544/1033173712"
            )
        }
    }

}
