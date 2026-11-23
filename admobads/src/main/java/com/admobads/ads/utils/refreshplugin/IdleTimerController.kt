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

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.Executor
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Class to manage banner rotation idle time. */
internal class IdleTimerController(
  private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
  private val timeSource: TimeSource = TimeSource.Monotonic,
  private val mainThreadExecutor: Executor = Executor { Handler(Looper.getMainLooper()).post(it) },
) : DefaultLifecycleObserver {
  private val lock = Any()

  /** A value of null indicates the timer is idle / paused. */
  internal var startTimeMark: TimeMark? = null
  /** The total time accumulated, in milliseconds. */
  internal var accumulatedMillis: Long = 0L

  /** Represents the possible states of the timer. */
  private enum class State {
    /** Default state for the timer. */
    STOPPED,
    /** The timer is actively counting. */
    RUNNING,
    /** The timer is not counting. */
    PAUSED,
  }

  /** The source of truth for the timer's current state. */
  private var state: State = State.STOPPED

  init {
    // Always add the observer on the main thread.
    mainThreadExecutor.execute { lifecycle.addObserver(this@IdleTimerController) }
  }

  /** Lifecycle callback for when the app enters the foreground. */
  override fun onStart(owner: LifecycleOwner) {
    resume()
  }

  /** Lifecycle callback for when the app enters the background. */
  override fun onStop(owner: LifecycleOwner) {
    pause()
  }

  /** Returns the elapsed idle time in milliseconds. */
  private val idleTimeInMillis: Long
    get() =
      synchronized(lock) {
        val runningMillis =
          if (state == State.RUNNING) {
            startTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
          } else {
            0L
          }
        accumulatedMillis + runningMillis
      }

  /** The total accumulated time since the timer was started. */
  internal val idleTimeInSeconds: Int
    get() = (idleTimeInMillis / 1000L).toInt()

  /** Starts the timer. If the timer is already running, this method does nothing. */
  fun start() =
    synchronized(lock) {
      if (state == State.STOPPED) {
        startTimeMark = timeSource.markNow()
        state = State.RUNNING
      }
    }

  /** If the timer is running, pause it and store the total accumulated time. */
  fun pause() =
    synchronized(lock) {
      if (state == State.RUNNING) {
        startTimeMark?.let { mark ->
          accumulatedMillis += mark.elapsedNow().inWholeMilliseconds
          startTimeMark = null
          state = State.PAUSED
        }
      }
    }

  fun resume() =
    synchronized(lock) {
      if (state == State.PAUSED) {
        startTimeMark = timeSource.markNow()
        state = State.RUNNING
      }
    }

  /** Stops the timer and resets the accumulated time to zero. */
  fun resetAndStopTimer() =
    synchronized(lock) {
      accumulatedMillis = 0L
      startTimeMark = null
      state = State.STOPPED
    }

  fun destroy() {
    // Remove the observer to prevent memory leaks.
    mainThreadExecutor.execute { lifecycle.removeObserver(this@IdleTimerController) }
    resetAndStopTimer()
  }
}
