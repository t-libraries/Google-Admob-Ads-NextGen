// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.ads.googlebannerrefresh

import android.util.Log
import com.admobads.ads.utils.refreshplugin.AdSource
import com.admobads.ads.utils.refreshplugin.Constants

/**
 * Configuration for banner refresh.
 *
 * @property adSourceRefreshRatesInSeconds A map where the keys are ad source IDs and the values are
 *   their desired refresh rates in seconds. Defaults to an empty map.
 * @property adsBufferSize The maximum number of preloaded ads with [com.admobads.ads.utils.refreshplugin.BannerRefreshView]'s internal
 *   cache. The accepted range is between 1 and 10. Attempts to set a value outside this range will
 *   result in the value being clamped to the nearest valid bound. Defaults to 3.
 * @property defaultRefreshRateInSeconds The default refresh rate in seconds to use for ad sources
 *   that are not explicitly defined in [adSourceRefreshRatesInSeconds]. The accepted range is
 *   between 5 and 150 seconds. Attempts to set a value outside this range will result in the value
 *   being clamped to the nearest valid bound. Defaults to 120 seconds.
 */
class BannerRefreshConfiguration
private constructor(
  val adsBufferSize: Int,
  val defaultRefreshRateInSeconds: Int,
  val adSourceRefreshRatesInSeconds: Map<String, Int>,
) {
  /** Builder for [BannerRefreshConfiguration]/ */
  class Builder {
    internal var adsBufferSize: Int = DEFAULT_ADS_BUFFER_SIZE
    internal var defaultRefreshRateInSeconds: Int = DEFAULT_REFRESH_RATE
    internal var adSourceRefreshRatesInSeconds: Map<String, Int> = emptyMap()

    /** Sets the initial ads buffer size. */
    fun setAdsBufferSize(adsBufferSize: Int): Builder {
      val clampedAdsBufferSize = adsBufferSize.coerceIn(MIN_ADS_BUFFER_SIZE, MAX_ADS_BUFFER_SIZE)
      if (clampedAdsBufferSize != adsBufferSize) {
        Log.w(
          Constants.TAG,
          "Attempted to set ads buffer size to $adsBufferSize. Setting to $clampedAdsBufferSize.",
        )
      }
      this.adsBufferSize = clampedAdsBufferSize
      return this
    }

    /** Sets the default refresh rate. */
    fun setDefaultRefreshRateInSeconds(defaultRefreshRateInSeconds: Int): Builder {
      val clampedRefreshRate = getClampedRefreshRate(defaultRefreshRateInSeconds)
      this.defaultRefreshRateInSeconds = clampedRefreshRate
      return this
    }

    /** Sets a refresh rate to an ad source ID. */
    fun setAdSourceRefreshRatesInSeconds(adSourceRefreshRatesInSeconds: Map<String, Int>): Builder {
      val clampedRates = mutableMapOf<String, Int>()
      adSourceRefreshRatesInSeconds.forEach { (adSourceId, refreshRate) ->
        val clampedRefreshRate = getClampedRefreshRate(refreshRate)
        clampedRates[adSourceId] = clampedRefreshRate
      }

      // If ad sources includes ADMOB_NETWORK, write a matching value for ADMOB_NETWORK_WATERFALL.
      val admobNetworkRate = clampedRates[AdSource.ADMOB_NETWORK]
      if (admobNetworkRate != null) {
        clampedRates[AdSource.ADMOB_NETWORK_WATERFALL] = admobNetworkRate
      }

      this.adSourceRefreshRatesInSeconds = clampedRates
      return this
    }

    private fun getClampedRefreshRate(value: Int): Int {
      val clampedValue = value.coerceIn(MIN_REFRESH_RATE, MAX_REFRESH_RATE)
      if (clampedValue != value) {
        Log.w(
          Constants.TAG,
          "Attempted to set refresh rate to ${value}s. Setting to ${clampedValue}s.",
        )
      }
      return clampedValue
    }

    /** Constructs a [BannerRefreshConfiguration]. */
    fun build(): BannerRefreshConfiguration =
      BannerRefreshConfiguration(
        adsBufferSize,
        defaultRefreshRateInSeconds,
        adSourceRefreshRatesInSeconds,
      )
  }

  private companion object {
    const val MIN_ADS_BUFFER_SIZE = 1
    const val MAX_ADS_BUFFER_SIZE = 10
    const val MIN_REFRESH_RATE = 5
    const val MAX_REFRESH_RATE = 150

    // Default values.
    const val DEFAULT_ADS_BUFFER_SIZE = 3
    const val DEFAULT_REFRESH_RATE = 120
  }
}
