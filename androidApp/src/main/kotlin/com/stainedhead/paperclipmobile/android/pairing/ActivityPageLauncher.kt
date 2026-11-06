package com.stainedhead.paperclipmobile.android.pairing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * The system implementation of [PageLauncher]. A page is shown from the resumed activity when there is one, otherwise
 * from the application context in a new task. Nothing here keeps or logs the address.
 */
class ActivityPageLauncher(
    private val context: Context,
    private val resumedActivity: () -> Activity?,
) : PageLauncher {
    override fun openCustomTab(url: String): Boolean {
        if (CustomTabsClient.getPackageName(context, null) == null) return false
        val intent =
            CustomTabsIntent
                .Builder()
                .build()
                .intent
                .setData(url.toUri())
        return start(intent)
    }

    override fun openBrowser(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun start(intent: Intent): Boolean {
        val activity = resumedActivity()
        return try {
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
