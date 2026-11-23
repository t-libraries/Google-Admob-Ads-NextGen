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

import android.util.Log

/** A logger for the banner refresh plugin. */
internal object BannerRefreshLogger {
  /**
   * Logs a message with a banner ID if logging is enabled.
   *
   * @param event The event to log.
   * @param message The message to log.
   * @param id The unique identifier for the banner instance.
   */
  internal fun log(event: Event, message: String, id: Int) {
    if (!BannerRefreshView.isDebugLoggingEnabled) return

    Log.d(Constants.TAG, "[ID: $id] [${event.name}] $message")
  }

  /** Represents a semantic logging event. */
  internal enum class Event {
    AdVisible,
    LoadAd,
    LoadAdFailure,
    LoadAdSuccess,
    RotateFailure,
    RotateSuccess,
    Start,
  }
}
