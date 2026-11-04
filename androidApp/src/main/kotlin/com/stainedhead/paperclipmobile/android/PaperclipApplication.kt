package com.stainedhead.paperclipmobile.android

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ProcessLifecycleOwner
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.AppGraphProvider
import com.stainedhead.paperclipmobile.android.env.DemoGraphProvider
import com.stainedhead.paperclipmobile.android.env.FirstRunDemoGraphProvider
import com.stainedhead.paperclipmobile.android.env.ForegroundObserver
import com.stainedhead.paperclipmobile.android.env.LaunchConfiguration
import com.stainedhead.paperclipmobile.android.env.LiveGraphProvider
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import kotlinx.coroutines.MainScope

/**
 * Owns the one [AppEnvironment] of the process (AND-010). The graph is opened lazily by the first activity, because the
 * launch intent (demo extras, debug builds only) is only visible there, and never in `onCreate` of the application: the
 * Keystore and the database are opened where a failure can be shown. Rotation and recreation reuse it.
 */
class PaperclipApplication : Application() {
    private var environment: AppEnvironment? = null

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundObserver { environment?.setForeground(it) })
    }

    /**
     * The environment of this process; the first call decides which graph it runs on, from [intent] (debug builds) or
     * the live graph with [pairingBrowser]. Later calls return the same environment.
     */
    @Synchronized
    fun environment(
        intent: Intent?,
        pairingBrowser: PairingBrowser,
    ): AppEnvironment =
        environment ?: run {
            val launch = LaunchConfiguration.from(intent, BuildConfig.DEBUG)
            AppEnvironment(
                providerFor(launch, pairingBrowser),
                MainScope(),
                BuildConfig.DEBUG,
            ).also { environment = it }
        }

    private fun providerFor(
        launch: LaunchConfiguration,
        pairingBrowser: PairingBrowser,
    ): AppGraphProvider =
        when {
            launch.useFirstRunDemo -> FirstRunDemoGraphProvider()
            launch.useDemo -> DemoGraphProvider()
            else -> LiveGraphProvider(this, pairingBrowser)
        }
}
