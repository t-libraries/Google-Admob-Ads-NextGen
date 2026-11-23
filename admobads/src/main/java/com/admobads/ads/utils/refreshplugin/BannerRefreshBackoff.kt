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

package com.admobads.ads.utils.refreshplugin

import kotlin.math.min
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implements backoff strategy.
 *
 * @param adsBufferSize The size of the ad buffer.
 * @param refreshRateInSeconds The refresh rate in seconds.
 */
class BannerRefreshBackoff(adsBufferSize: Int, refreshRateInSeconds: Int) {
  private val mutex = Mutex()
  private val maxBackoffInSeconds: Int = adsBufferSize * refreshRateInSeconds
  private var currentBackoffInSeconds: Int = INITIAL_BACKOFF_IN_SECONDS

  /** Resets the current backoff. */
  suspend fun resetBackoff() {
    mutex.withLock { currentBackoffInSeconds = INITIAL_BACKOFF_IN_SECONDS }
  }

  /** Increases the current backoff. */
  suspend fun increaseBackoff() {
    mutex.withLock {
      currentBackoffInSeconds = min(currentBackoffInSeconds * MULTIPLIER, maxBackoffInSeconds)
    }
  }

  /**
   * Gets the current backoff in seconds.
   *
   * @return The current backoff in seconds.
   */
  suspend fun getCurrentBackoffInSeconds(): Int = mutex.withLock { currentBackoffInSeconds }

  private companion object {
    private const val INITIAL_BACKOFF_IN_SECONDS: Int = 1
    private const val MULTIPLIER: Int = 2
  }
}
