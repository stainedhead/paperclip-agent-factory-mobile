package com.stainedhead.paperclipmobile.android.shell

import android.view.Window
import android.view.WindowManager

/**
 * What keeps the app's content out of screenshots and the recents thumbnail (AND-014).
 *
 * - The lock screen and the pairing step are never captured: [setScreenSecure] sets `FLAG_SECURE` while they show.
 * - The recents thumbnail is hidden always: from Android 13 the activity turns the recents screenshot off ([onCreate]);
 *   below that the window is secure while the activity is paused, which is when the system takes the thumbnail.
 *
 * Screenshots elsewhere in the app are allowed (main PRD non-functional requirements).
 */
class PrivacyShield(
    private val window: Window,
    private val sdkInt: Int,
    private val setRecentsScreenshotEnabled: (Boolean) -> Unit,
) {
    private var screenSecure = false

    fun onCreate() {
        if (sdkInt >= ANDROID_13) setRecentsScreenshotEnabled(false)
    }

    /** True while the lock screen or the pairing step shows; false everywhere else. */
    fun setScreenSecure(secure: Boolean) {
        screenSecure = secure
        applyFlag(secure)
    }

    fun onPause() {
        if (sdkInt < ANDROID_13) applyFlag(true)
    }

    fun onResume() = applyFlag(screenSecure)

    private fun applyFlag(secure: Boolean) {
        if (secure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private companion object {
        const val ANDROID_13 = 33
    }
}
