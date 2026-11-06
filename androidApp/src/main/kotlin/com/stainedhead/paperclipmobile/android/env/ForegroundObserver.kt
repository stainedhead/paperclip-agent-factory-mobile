package com.stainedhead.paperclipmobile.android.env

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Tells the app when the process comes to the foreground (some activity is started) or goes to the background (none
 * is). Registered on `ProcessLifecycleOwner`; the environment passes it on to `graph.lifecycle.setForeground`, which
 * stops or resumes the refresh policy and re-engages the app lock after the idle period (PRD FR-014, FR-090).
 */
class ForegroundObserver(
    private val report: (isForeground: Boolean) -> Unit,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = report(true)

    override fun onStop(owner: LifecycleOwner) = report(false)
}
