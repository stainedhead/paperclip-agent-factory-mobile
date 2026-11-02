package com.stainedhead.paperclipmobile.infrastructure.persistence

import android.content.Context
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
 * Not covered by a test in this repository: the Android framework SQLite classes do not run on the host JVM and no
 * emulator test is set up yet (the shared contract suites run against the JDBC driver instead).
 */
fun openAndroidPersistence(
    context: Context,
    clock: Clock,
): PaperclipPersistence {
    val appContext = context.applicationContext
    val opened =
        openRecovering(
            open = { AndroidSqliteDriver(openHelper(appContext)) },
            wipe = { SIDECAR_SUFFIXES.forEach { File(appContext.noBackupFilesDir, DATABASE_NAME + it).delete() } },
        )
    return PaperclipPersistence(opened.driver, clock, opened.recreated)
}

private fun openHelper(context: Context): SupportSQLiteOpenHelper =
    FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(DATABASE_NAME)
            .noBackupDirectory(true)
            .callback(AndroidSqliteDriver.Callback(PaperclipDatabase.Schema))
            .build(),
    )
