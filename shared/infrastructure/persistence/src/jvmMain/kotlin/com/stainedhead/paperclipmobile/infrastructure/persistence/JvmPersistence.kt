package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import java.io.File

private val SIDECAR_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

/**
 * The JVM database: the file at [file], or an in-memory database when it is null (tests and tooling; the apps use the
 * iOS and Android functions). A file that cannot be used is recreated empty, see [openRecovering].
 */
fun openJvmPersistence(
    clock: Clock,
    file: File? = null,
): PaperclipPersistence {
    val url = if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}"
    val opened =
        openRecovering(
            open = { JdbcSqliteDriver(url).also { it.prepareSchema() } },
            wipe = { file?.let { f -> SIDECAR_SUFFIXES.forEach { File(f.path + it).delete() } } },
        )
    return PaperclipPersistence(opened.driver, clock, opened.recreated)
}

/**
 * The JDBC driver keeps no schema version of its own, so this keeps SQLite's `user_version` in step: 0 means a new
 * file (create), lower than the code means migrate, higher is left for the health check to reject.
 */
internal fun SqlDriver.prepareSchema() {
    val current = PaperclipDatabase.Schema.version
    val stored = scalarLong("PRAGMA user_version")
    when {
        stored == 0L -> PaperclipDatabase.Schema.create(this)
        stored < current -> PaperclipDatabase.Schema.migrate(this, stored, current)
        else -> return
    }
    execute(null, "PRAGMA user_version = $current", 0)
}
