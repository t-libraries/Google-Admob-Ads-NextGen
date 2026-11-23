package com.google.android.ads.googlebannerrefresh

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.view.View

/** A general utility class for the plugin. */
object AdUtil {
  /**
   * Checks if the provided banner view is currently visible on the screen.
   *
   * @param adView The banner [View] to check for visibility.
   * @return `true` if the view is currently visible on the screen, `false` otherwise.
   */
  internal fun isAdViewVisible(adView: View): Boolean {
    // A view can't be visible if it or its ancestors are not VISIBLE.
    if (!adView.isShown) {
      return false
    }

    // AdView is considered visible if screen is on and lock screen is off.
    return isInteractive(adView.context) && !isKeyguardLocked(adView.context)
  }

  private fun isInteractive(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    // PowerManager is a core service and should always be returned. If null, we assume
    // interactivity to avoid interrupting ad refreshes.
    return powerManager?.isInteractive ?: true
  }

  private fun isKeyguardLocked(context: Context): Boolean {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    // KeyguardManager is a core service and should always be returned. If null, we assume
    // keyguard is not locked to avoid interrupting ad refreshes.
    return keyguardManager?.isKeyguardLocked ?: false
  }
}
