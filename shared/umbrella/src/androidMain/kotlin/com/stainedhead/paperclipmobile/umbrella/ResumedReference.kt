package com.stainedhead.paperclipmobile.umbrella

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * The one resumed object (an activity) of the process, held weakly so a registry that outlives the activity never keeps
 * it alive. A newer registration replaces the older; an older one pausing late does not clear the newer.
 */
internal class ResumedReference<T : Any> {
    private val held = AtomicReference<WeakReference<T>?>(null)

    fun register(value: T) = held.set(WeakReference(value))

    /** Clears the registration if [value] is still the registered one. */
    fun unregister(value: T) {
        val current = held.get()
        if (current?.get() === value) held.compareAndSet(current, null)
    }

    fun current(): T? = held.get()?.get()
}
