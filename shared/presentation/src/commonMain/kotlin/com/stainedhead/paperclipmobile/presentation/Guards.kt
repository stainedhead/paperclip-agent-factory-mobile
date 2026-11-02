package com.stainedhead.paperclipmobile.presentation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Atomically takes a guard: sets it with [set] only if [isSet] says nobody holds it, and says whether this caller did.
 * A plain read followed by an update lets two threads both pass; this cannot (F-B-6).
 */
internal inline fun <M> MutableStateFlow<M>.claim(
    isSet: (M) -> Boolean,
    set: (M) -> M,
): Boolean {
    while (true) {
        val current = value
        if (isSet(current)) return false
        if (compareAndSet(current, set(current))) return true
    }
}
