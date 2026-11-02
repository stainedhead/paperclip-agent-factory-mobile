package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.numberWithBool

private const val DATABASE_NAME = "paperclip.db"
private const val DATA_FOLDER = "PaperclipData"
private val SIDECAR_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

/**
 * The iOS database in [directory] (default: `Application Support/PaperclipData`, not `Caches`, which iOS may purge).
 * The folder and the database files are excluded from iCloud and iTunes backups and use the
 * "complete until first user authentication" file protection class; both are applied again on every open because
 * recreating a file loses them. A file that cannot be used is recreated empty, see [openRecovering].
 */
fun openIosPersistence(
    clock: Clock,
    directory: String = iosDataDirectory(),
): PaperclipPersistence {
    prepareDirectory(directory)
    val opened =
        openRecovering(
            open = { nativeDriver(directory) },
            wipe = { SIDECAR_SUFFIXES.forEach { removeFile("$directory/$DATABASE_NAME$it") } },
        )
    val persistence = PaperclipPersistence(opened.driver, clock, opened.recreated)
    // After the first write: on a database that was just recreated the file flag set before it was gone afterwards.
    SIDECAR_SUFFIXES.map { "$directory/$DATABASE_NAME$it" }.filter(::fileExists).forEach(::protect)
    return persistence
}

internal fun iosDataDirectory(): String {
    val base =
        NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
    return "$base/$DATA_FOLDER"
}

private fun nativeDriver(directory: String): SqlDriver =
    NativeSqliteDriver(
        PaperclipDatabase.Schema,
        DATABASE_NAME,
        onConfiguration = { it.copy(extendedConfig = DatabaseConfiguration.Extended(basePath = directory)) },
    )

@OptIn(ExperimentalForeignApi::class)
private fun prepareDirectory(directory: String) {
    val created =
        NSFileManager.defaultManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
            error = null,
        )
    check(created) { "Could not create the data directory" }
    protect(directory)
}

/** Backup exclusion and file protection for one path. Failing to apply either fails loudly, not silently. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun protect(path: String) {
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val excluded =
            NSURL
                .fileURLWithPath(
                    path,
                ).setResourceValue(NSNumber.numberWithBool(true), NSURLIsExcludedFromBackupKey, error.ptr)
        check(excluded) { "Could not exclude the database from backups: ${error.value?.localizedDescription}" }
    }
    NSFileManager.defaultManager.setAttributes(
        mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        ofItemAtPath = path,
        error = null,
    )
}

private fun fileExists(path: String) = NSFileManager.defaultManager.fileExistsAtPath(path)

@OptIn(ExperimentalForeignApi::class)
private fun removeFile(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, null)
}
