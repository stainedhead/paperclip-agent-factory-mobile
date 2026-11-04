package com.stainedhead.paperclipmobile.android

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.ProcessLifecycleOwner
import com.stainedhead.paperclipmobile.android.diagnostics.AppLog
import com.stainedhead.paperclipmobile.android.diagnostics.DiagnosticsRecorder
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.AppGraphProvider
import com.stainedhead.paperclipmobile.android.env.DemoGraphProvider
import com.stainedhead.paperclipmobile.android.env.FirstRunDemoGraphProvider
import com.stainedhead.paperclipmobile.android.env.ForegroundObserver
import com.stainedhead.paperclipmobile.android.env.LaunchConfiguration
import com.stainedhead.paperclipmobile.android.env.LiveGraphProvider
import com.stainedhead.paperclipmobile.android.pairing.ActivityPageLauncher
import com.stainedhead.paperclipmobile.android.pairing.CustomTabsPairingBrowser
import com.stainedhead.paperclipmobile.android.pairing.PairingBrowserEvent
import kotlinx.coroutines.MainScope
import java.lang.ref.WeakReference

/**
 * Owns the one [AppEnvironment] of the process (AND-010). The graph is opened lazily by the first activity, because the
 * launch intent (demo extras, debug builds only) is only visible there, and never in `onCreate` of the application: the
 * Keystore and the database are opened where a failure can be shown. Rotation and recreation reuse it.
 */
class PaperclipApplication : Application() {
    private var environment: AppEnvironment? = null
    private var resumed: WeakReference<Activity>? = null

    /** Shows the pairing approval page; the activity reports its pauses and resumes to it. */
    val pairingPage: CustomTabsPairingBrowser by lazy {
        CustomTabsPairingBrowser(ActivityPageLauncher(this) { resumed?.get() }, BuildConfig.DEBUG, ::pairingPageEvent)
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundObserver { environment?.setForeground(it) })
    }

    /** The activity that is resumed now, or null; the pairing page is shown from it. */
    fun setResumed(activity: Activity?) {
        resumed = activity?.let { WeakReference(it) }
    }

    /**
     * The environment of this process; the first call decides which graph it runs on, from [intent] (debug builds) or
     * the live graph. Later calls return the same environment.
     */
    @Synchronized
    fun environment(intent: Intent?): AppEnvironment =
        environment ?: run {
            val demoControls = BuildConfig.DEBUG || BuildConfig.DEMO_ENTRY
            val launch = LaunchConfiguration.from(intent, demoControls)
            AppEnvironment(
                providerFor(launch),
                MainScope(),
                demoControls,
                DiagnosticsRecorder(BuildConfig.VERSION_NAME),
                AppLog(BuildConfig.DEBUG),
            ).also {
                environment = it
                launch.scriptedBiometrics.forEach { result ->
                    it.session.value.graph
                        ?.demoControls
                        ?.scriptBiometrics(result)
                }
            }
        }

    private fun providerFor(launch: LaunchConfiguration): AppGraphProvider =
        when {
            launch.useFirstRunDemo -> FirstRunDemoGraphProvider()
            launch.useDemo -> DemoGraphProvider(launch.inboxCards)
            else -> LiveGraphProvider(this, pairingPage.port)
        }

    /** The page could not be shown: the pairing on screen is cancelled instead of waiting for nothing. */
    private fun pairingPageEvent(event: PairingBrowserEvent) {
        if (event == PairingBrowserEvent.CouldNotPresent) {
            environment
                ?.session
                ?.value
                ?.cancelPairingAttempts()
        }
    }
}
