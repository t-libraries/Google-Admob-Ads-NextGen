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

        AdmobAdManger.intializeSdk(this, getString(R.string.appid)) {
            AdmobInterstitialAd.getInstance().initInterFromConfig(
                this@MyApplication,
                InterAdModel(
                    inter_type = "timer",
                    loading_type = "api",
                    inter_counter_start = 1,
                    inter_counter_gap = 1,
                    inter_start_after_seconds = 15,
                    inter_start_load_before_seconds = 5,
                    inter_gap_after_seconds = 30,
                    inter_gap_load_before_seconds = 25
                ),
                "ca-app-pub-3940256099942544/1033173712"
            )
        }
    }


}
