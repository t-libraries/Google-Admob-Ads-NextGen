package com.admobads.ads.utils

import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds

/**
 * Guards ad API calls until [MobileAds.initialize] has completed.
 * Next-Gen SDK throws if ads are loaded before initialization.
 */
object AdmobSdkGuard {

    private const val TAG = "AdmobSdkGuard"

    @JvmStatic
    fun isInitialized(): Boolean {
        return try {
            MobileAds.isInitialized
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read MobileAds.isInitialized", e)
            false
        }
    }

    /**
     * @return true when the SDK is ready; false when the caller should abort.
     */
    @JvmStatic
    fun ensureInitialized(caller: String): Boolean {
        if (isInitialized()) {
            return true
        }
        Log.w(TAG, "$caller: MobileAds.initialize must be called before using ads")
        return false
    }
}
