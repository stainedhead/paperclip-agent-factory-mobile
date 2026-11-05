package com.stainedhead.paperclipmobile.infrastructure.persistence

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import java.io.File

private const val DATABASE_NAME = "paperclip.db"
private val SIDECAR_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

/**
 * The Android database. It lives in `noBackupFilesDir` (the open helper's `noBackupDirectory` option), which Android
 * Auto Backup and device transfer skip, so the factory list never leaves the phone. A file that cannot be used is
 * recreated empty, see [openRecovering].
 *
 * The Android framework SQLite classes do not run on the host JVM, so `AndroidPersistenceDeviceTest` (emulator) covers
 * this function; the shared contract suites run against the JDBC driver.
 */
fun openAndroidPersistence(
    context: Context,
    clock: Clock,
): PaperclipPersistence {
    val appContext = context.applicationContext
    val marker = pendingMarker(File(appContext.noBackupFilesDir, "$DATABASE_NAME.sweep-pending"))
    val opened =
        openRecovering(
            open = { AndroidSqliteDriver(openHelper(appContext)) },
            wipe = { SIDECAR_SUFFIXES.forEach { File(appContext.noBackupFilesDir, DATABASE_NAME + it).delete() } },
            marker = marker,
        )
    return PaperclipPersistence(opened.driver, clock, opened.recreated, marker)
}

private fun openHelper(context: Context): SupportSQLiteOpenHelper =
    FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(DATABASE_NAME)
            .noBackupDirectory(true)
            .callback(KeepCorruptFileCallback())
            .build(),
    )

/**
 * The framework's default reaction to a database it cannot read is to delete the file and open a fresh empty one,
 * without telling anyone. That would skip the recreation marker, the startup notice and the orphan secret sweep
 * (F-R-1), so the file is left alone here; the open then fails and [openRecovering] decides, sets the marker and wipes.
 */
private class KeepCorruptFileCallback : AndroidSqliteDriver.Callback(PaperclipDatabase.Schema) {
    override fun onCorruption(db: SupportSQLiteDatabase) = Unit
}

private fun pendingMarker(file: File): SweepMarker =
    FileMarker(
        exists = { file.exists() },
        create = { check(file.createNewFile() || file.exists()) { "Could not write the recreation marker" } },
        delete = { file.delete() },
    )
