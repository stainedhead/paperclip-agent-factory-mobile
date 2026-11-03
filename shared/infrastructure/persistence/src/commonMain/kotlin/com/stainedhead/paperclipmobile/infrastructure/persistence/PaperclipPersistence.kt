package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.SqlDriver
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The three SQLite-backed ports over one database (AD-021), built by the per-platform `open…Persistence` functions.
 * [recreated] is true when the database file could not be used and was replaced by an empty one (ADR-042), or
 * when an earlier recreation has not been followed by a completed sweep yet ([sweepCompleted]).
 */
class PaperclipPersistence internal constructor(
    private val driver: SqlDriver,
    clock: Clock,
    val recreated: Boolean,
    private val marker: SweepMarker = InMemoryMarker(),
) {
    private val db = PaperclipDatabase(driver)
    private val dispatcher = serialDispatcher()

    val instances = SqlInstanceRepository(db, dispatcher)
    val settings = SqlAppSettingsStore(db, dispatcher)
    val attentionCache = SqlAttentionCache(db, clock, dispatcher)

    init {
        // A page cached for a factory that was removed while its refresh was in flight has no owner: drop it.
        db.attentionCacheQueries.deleteOrphans()
    }

    /** The orphan sweep after a recreation has completed: the next launch is silent again (F-R-1). */
    fun sweepCompleted() {
        marker.clear()
    }

    /** Releases the database file. Nothing may be used afterwards. */
    fun close() {
        driver.close()
    }
}

/**
 * One executor with parallelism 1 for a whole database. Every query and transaction of the three ports runs on it, so
 * a transaction never interleaves with another thread's statements, whatever the driver does with its connections
 * (the JDBC in-memory driver shares one connection and fails with "cannot start a transaction within a transaction").
 */
internal fun serialDispatcher(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
