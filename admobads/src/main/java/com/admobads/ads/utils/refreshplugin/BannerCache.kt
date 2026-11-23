// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.admobads.ads.utils.refreshplugin

import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Manages a cache of banner ad instances. */
class BannerCache {

  private val mutex = Mutex()
  /** List of banner ads that have started loading but have not yet finished loading. */
  private val unloadedBanners = mutableListOf<BannerAd>()
  /** List of banner ads that have successfully loaded. */
  private val loadedBanners = mutableListOf<BannerAd>()

  /**
   * Adds a banner ad to the loaded banners cache.
   *
   * Next-Gen loads via [BannerAd.load], so there is no unloaded AdView to track first.
   * Always accept the loaded ad into the ready buffer.
   */
  suspend fun addLoaded(banner: BannerAd) {
    mutex.withLock {
      unloadedBanners.remove(banner)
      if (!loadedBanners.contains(banner)) {
        loadedBanners.add(banner)
      }
    }
  }

  /**
   * Adds a banner ad to the unloaded banners cache.
   *
   * @param banner The banner ad to be added.
   */
  suspend fun addUnloaded(banner: BannerAd) {
    mutex.withLock { unloadedBanners.add(banner) }
  }

  /**
   * Gets the size of the loaded banners cache.
   *
   * @return The size of the loaded banners cache.
   */
  suspend fun getLoadedSize(): Int {
    return mutex.withLock { loadedBanners.size }
  }

  /**
   * Pops the next banner ad from the loaded banners cache.
   *
   * @return A banner ad, or null if the loaded banners cache is empty.
   */
  suspend fun popLoadedBannerAd(): BannerAd? {
    return mutex.withLock {
      if (loadedBanners.isNotEmpty()) {
        loadedBanners.removeAt(0)
      } else {
        null
      }
    }
  }



  /** Cleans up resources held by the cache. */
  fun destroy() {
    for (banner in unloadedBanners) {
      banner.destroy()
    }
    for (banner in loadedBanners) {
      banner.destroy()
    }
    unloadedBanners.clear()
    loadedBanners.clear()
  }
}
