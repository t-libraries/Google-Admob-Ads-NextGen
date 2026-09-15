package com.transfer.googleadmobnextgensdk

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.admobads.AdmobAdManger
import com.admobads.ads.AdmobInterstitialAd
import com.admobads.data.InterAdModel
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.component1
import kotlin.collections.component2

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

        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Admob SDK Start Intializing")
            val startMs = System.currentTimeMillis()

            MobileAds.initialize(
                this@MyApplication,
                InitializationConfig.Builder(getString(R.string.appid)).build()
            ) { status ->
                Log.d(
                    TAG,
                    "Admob adapters finished in ${System.currentTimeMillis() - startMs}ms"
                )
                status.adapterStatusMap.forEach { (name, adapterStatus) ->
                    Log.d(
                        TAG,
                        "Adapter=$name state=${adapterStatus.initializationState} " +
                                "latency=${adapterStatus.latency} desc=${adapterStatus.description}"
                    )
                }
            }

            withContext(Dispatchers.Main){
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

}