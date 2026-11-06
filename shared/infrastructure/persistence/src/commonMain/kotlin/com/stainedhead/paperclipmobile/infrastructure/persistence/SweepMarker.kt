package com.stainedhead.paperclipmobile.infrastructure.persistence

/**
 * A durable "the database was recreated and the orphan sweep has not finished" flag (F-R-1). It is set BEFORE the
 * file is wiped and cleared only after the sweep completed, so a failed reopen or a kill after the wipe still shows
 * the startup notice and runs the sweep on the next launch. It holds no secret and no data, only its own existence.
 */
internal interface SweepMarker {
    fun isSet(): Boolean

    fun set()

    fun clear()
}

/** A marker held in memory: used where there is no file (in-memory databases and tests). */
internal class InMemoryMarker(
    private var value: Boolean = false,
) : SweepMarker {
    override fun isSet() = value

    override fun set() {
        value = true
    }

    override fun clear() {
        value = false
    }
}

/** A marker kept as a file: the platform supplies how to test for, create and delete it. */
internal class FileMarker(
    private val exists: () -> Boolean,
    private val create: () -> Unit,
    private val delete: () -> Unit,
) : SweepMarker {
    override fun isSet() = exists()

    override fun set() = create()

    override fun clear() = delete()
}
