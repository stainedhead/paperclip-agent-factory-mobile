package com.stainedhead.paperclipmobile.android.auth

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.stainedhead.paperclipmobile.umbrella.AndroidAuthHost

/**
 * Registers the activity as the place the system authentication prompt can be shown, only while it is resumed
 * (ADR-123). While none is registered the app is in the background, no prompt can be shown and a destructive action is
 * blocked, never approved. The activity adds this observer before it sets its content, so the registration runs before
 * any screen that reacts to RESUMED asks for the prompt.
 */
class AuthHostRegistration(
    private val activity: FragmentActivity,
) : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) = AndroidAuthHost.register(activity)

    override fun onPause(owner: LifecycleOwner) = AndroidAuthHost.unregister(activity)
}
