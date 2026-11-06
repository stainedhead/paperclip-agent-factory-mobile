package com.stainedhead.paperclipmobile.domain

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * A credential. It prints as `Secret(***)`, compares by value, and can be wiped from memory once stored.
 *
 * This is deliberately not a data class: no `component1` or `copy` can leak the value. Only the code that talks to the
 * server or to the device secret store may call [reveal]; a Konsist rule enforces the allowed packages (PRD FR-010).
 */
@OptIn(ExperimentalObjCRefinement::class)
class Secret private constructor(
    private var chars: CharArray,
) {
    /** True after [wipe]. */
    var isWiped: Boolean = false
        private set

    @HiddenFromObjC
    fun reveal(): String = chars.concatToString()

    /**
     * True when the value begins with [prefix]. Lets callers check a key's shape without reading it: the characters
     * are compared in place, so no `String` copy of the key (which [wipe] could not clear) is ever created.
     */
    fun startsWith(prefix: String): Boolean =
        prefix.length <= chars.size && prefix.indices.all { chars[it] == prefix[it] }

    /**
     * An independent copy of the value, made without creating a `String`. Whoever holds the copy wipes it on its own,
     * so a holder that hands the value out more than once (the unlock-session cache, ADR-145) gives each user a copy.
     */
    @HiddenFromObjC
    fun copy(): Secret = Secret(chars.copyOf())

    /** Overwrites the in-memory copy. The instance stays usable but holds an empty value. */
    fun wipe() {
        chars.fill('\u0000')
        chars = CharArray(0)
        isWiped = true
    }

    override fun equals(other: Any?): Boolean = other is Secret && other.chars.contentEquals(chars)

    override fun hashCode(): Int = chars.contentHashCode()

    override fun toString(): String = "Secret(***)"

    companion object {
        fun of(value: String): Secret = Secret(value.toCharArray())
    }
}
