package com.stainedhead.paperclipmobile.android

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.stainedhead.paperclipmobile.android.auth.AuthHostRegistration
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.screens.FirstRunScreen
import com.stainedhead.paperclipmobile.android.shell.AppTabs
import com.stainedhead.paperclipmobile.android.shell.LockScreen
import com.stainedhead.paperclipmobile.android.shell.PrivacyShield
import com.stainedhead.paperclipmobile.android.shell.RootScreen

/**
 * The one activity. It is a `FragmentActivity` because `BiometricPrompt` needs one. It registers itself as the place
 * the system prompt can be shown while it is resumed (the shared use cases ask for the prompt, never a screen), keeps
 * the window out of screenshots while the lock or the pairing step shows, and hides the recents thumbnail always.
 */
class MainActivity : FragmentActivity() {
    private lateinit var privacy: PrivacyShield

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PaperclipApplication
        val environment = app.environment(intent)
        privacy =
            PrivacyShield(window, Build.VERSION.SDK_INT) { enabled ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(enabled)
            }
        privacy.onCreate()
        // Added before the content, so these run before any screen that reacts to RESUMED asks for the prompt.
        lifecycle.addObserver(AuthHostRegistration(this))
        lifecycle.addObserver(PairingPageObserver(this, app))
        setContent {
            PaperclipTheme {
                RootScreen(
                    environment = environment,
                    lock = { LockScreen(it) },
                    firstRun = { env, session ->
                        FirstRunScreen(session, onTryDemo = if (env.demoEntryAvailable) env::useDemo else null)
                    },
                    tabs = { session, badge -> AppTabs(session, badge) },
                    onSecureChanged = privacy::setScreenSecure,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        privacy.onResume()
    }

    override fun onPause() {
        privacy.onPause()
        super.onPause()
    }
}

/** Tells the application which activity is resumed and the pairing page when the person leaves and comes back. */
private class PairingPageObserver(
    private val activity: Activity,
    private val app: PaperclipApplication,
) : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
        app.setResumed(activity)
        app.pairingPage.onActivityResumed()
    }

    override fun onPause(owner: LifecycleOwner) {
        app.pairingPage.onActivityPaused()
        app.setResumed(null)
    }
}
