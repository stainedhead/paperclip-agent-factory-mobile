package com.stainedhead.paperclipmobile.android.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.stainedhead.paperclipmobile.android.pairing.ActivityPageLauncher
import com.stainedhead.paperclipmobile.android.pairing.PageLauncher
import com.stainedhead.paperclipmobile.android.pairing.acceptablePage

/**
 * Opens a web address the shared state gave the screen (the instance's issue page for a request the app cannot answer).
 * Only an `https` address opens: anything else is refused before the system is asked, so a link from a server is never
 * handed over as another scheme. It carries no header and no token; the web app asks for its own sign-in. It opens in a
 * Custom Tab when there is one, otherwise in the browser, and returns false when nothing could show the page.
 */
class WebOpener(
    private val launcher: PageLauncher,
) {
    fun open(url: String?): Boolean {
        val page = url?.let { acceptablePage(it, allowsInsecureLocal = false) } ?: return false
        return launcher.openCustomTab(page) || launcher.openBrowser(page)
    }
}

/** Tests provide a recording opener; the app uses the system one. */
val LocalWebOpener = compositionLocalOf<WebOpener?> { null }

@Composable
fun currentWebOpener(): WebOpener {
    val provided = LocalWebOpener.current
    val context = LocalContext.current
    return provided ?: remember(context) { WebOpener(ActivityPageLauncher(context) { context.hostActivity() }) }
}

private tailrec fun Context.hostActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.hostActivity()
        else -> null
    }
