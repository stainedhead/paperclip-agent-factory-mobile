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
 * Opens the database, and if it cannot be used recreates it empty (decision: recreate, do not fail closed).
 *
 * The file holds only non-secret configuration and a cache, so recreating loses the factory list, not any
 * credential. The board keys stay in the SecretStore; recreating orphans them (ADR-042) and the person adds the
 * factories again. Failing closed would leave the app unusable with no way out except reinstalling.
 *
 * "Cannot be used" means: opening or the first statement throws (SQLite accepts a garbage file until it is read), the
 * file was written by a newer schema version, or a table is missing. Only the second open may fail loudly.
 */
internal fun openRecovering(
    open: () -> SqlDriver,
    wipe: () -> Unit,
): OpenedDatabase {
    val driver = attemptOpen(open)
    if (driver != null) return OpenedDatabase(driver, recreated = false)
    wipe()
    return OpenedDatabase(open(), recreated = true)
}

private fun attemptOpen(open: () -> SqlDriver): SqlDriver? {
    val driver =
        try {
            open()
        } catch (_: Exception) {
            return null
        }
    if (driver.isUsable()) return driver
    driver.close()
    return null
}

private const val EXPECTED_TABLES = 3L

private fun SqlDriver.isUsable(): Boolean =
    try {
        scalarLong("PRAGMA user_version") <= PaperclipDatabase.Schema.version &&
            scalarLong(
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND " +
                    "name IN ('instance_config', 'app_settings', 'attention_cache')",
            ) == EXPECTED_TABLES
    } catch (_: Exception) {
        false
    }

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
