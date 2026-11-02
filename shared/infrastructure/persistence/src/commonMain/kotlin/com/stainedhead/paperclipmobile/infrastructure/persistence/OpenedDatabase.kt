package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase

/** A driver that is ready to use, and whether the database file had to be recreated empty to get it. */
internal class OpenedDatabase(
    val driver: SqlDriver,
    val recreated: Boolean,
)

/**
 * Opens the database, and recreates it empty only when the file is proven unusable (decision: recreate on proof, never
 * on a guess).
 *
 * The file holds only non-secret configuration and a cache, so recreating loses the factory list, not any
 * credential (ADR-042, ADR-077). It is wiped only for evidence that the content can never be read again: SQLite says
 * the file is not a database or is malformed, the platform refuses to open a file from a newer schema version, the
 * stored version is newer than the code, or an expected table is missing. Everything else (busy or locked, disk full
 * or I/O error, protected data not available yet, a migration that throws) is not proof: nothing is deleted and a
 * [StorageUnavailableException] is thrown so the caller can say storage is unavailable and try again later.
 */
internal fun openRecovering(
    open: () -> SqlDriver,
    wipe: () -> Unit,
): OpenedDatabase {
    val driver = attemptOpen(open)
    if (driver != null) return OpenedDatabase(driver, recreated = false)
    wipe()
    return OpenedDatabase(openAfterWipe(open), recreated = true)
}

private fun openAfterWipe(open: () -> SqlDriver): SqlDriver =
    runCatching(open).getOrElse { throw StorageUnavailableException(it) }

private fun attemptOpen(open: () -> SqlDriver): SqlDriver? {
    val opened = runCatching(open)
    val driver = opened.getOrNull() ?: return opened.exceptionOrNull().unusableOrThrow()
    val probe = runCatching { driver.isUsable() }
    if (probe.getOrNull() == true) return driver
    driver.close()
    return if (probe.isSuccess) null else probe.exceptionOrNull().unusableOrThrow()
}

/** Null when [this] proves the database is unusable (the caller wipes); otherwise the failure is not proof. */
private fun Throwable?.unusableOrThrow(): SqlDriver? {
    if (this !is Exception) throw this ?: error("open failed without a cause")
    if (provesUnusable()) return null
    throw StorageUnavailableException(this)
}

private val UNUSABLE_SIGNS = listOf("not a database", "malformed", "corrupt", "downgrade", "newer than")

private fun Throwable.provesUnusable(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        val text = current.message?.lowercase().orEmpty()
        if (UNUSABLE_SIGNS.any { it in text }) return true
        current = current.cause
        depth += 1
    }
    return false
}

private const val MAX_CAUSE_DEPTH = 8

private const val EXPECTED_TABLES = 3L

/** False when the stored version is newer or a table is missing; throws for any failure of the probe itself. */
private fun SqlDriver.isUsable(): Boolean =
    scalarLong("PRAGMA user_version") <= PaperclipDatabase.Schema.version &&
        scalarLong(
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND " +
                "name IN ('instance_config', 'app_settings', 'attention_cache')",
        ) == EXPECTED_TABLES

internal fun SqlDriver.scalarLong(sql: String): Long =
    executeQuery(
        null,
        sql,
        { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        0,
    ).value
