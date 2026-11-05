package com.stainedhead.paperclipmobile.umbrella

import androidx.fragment.app.FragmentActivity
import com.stainedhead.paperclipmobile.infrastructure.platform.AuthHost

/**
 * Where the live graph finds the activity that can show the system authentication prompt (ADR-123). The app's main
 * activity calls [register] when it resumes and [unregister] when it pauses; while none is registered the app is in the
 * background, no prompt can be shown and a destructive action is blocked, never approved. The reference is weak.
 */
object AndroidAuthHost : AuthHost {
    private val resumed = ResumedReference<FragmentActivity>()

    fun register(activity: FragmentActivity) = resumed.register(activity)

    fun unregister(activity: FragmentActivity) = resumed.unregister(activity)

    override suspend fun current(): FragmentActivity? = resumed.current()
}
