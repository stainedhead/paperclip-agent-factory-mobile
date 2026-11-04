package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val RECORD_SUFFIX = ".rec"
private const val TEMP_SUFFIX = ".tmp"
private const val SECRETS_DIRECTORY = "secrets"

/** A record file could not be read, written or deleted. */
internal class RecordFileException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The record files of the secret store, one per secret path, in [directory] (below the app's `noBackupFilesDir`, so
 * Auto Backup and device transfer skip them). The file name is the percent-encoded secret path, which lets the paths
 * be listed without opening or decrypting anything. A write goes to a temporary file in the same directory, is synced,
 * and replaces the record with one atomic rename: a reader sees the old record or the new one, never half of one.
 */
internal class RecordFiles(
    private val directory: File,
) {
    fun read(path: String): ByteArray? =
        try {
            file(path).takeIf { it.isFile }?.readBytes()
        } catch (e: IOException) {
            throw RecordFileException("a secret record could not be read", e)
        }

    fun write(
        path: String,
        bytes: ByteArray,
    ) {
        if (!directory.isDirectory && !directory.mkdirs()) throw RecordFileException("no secret directory")
        val target = file(path)
        val temp = File(directory, target.name.removeSuffix(RECORD_SUFFIX) + TEMP_SUFFIX)
        try {
            FileOutputStream(temp).use {
                it.write(bytes)
                it.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            throw RecordFileException("a secret record could not be written", e)
        } finally {
            temp.delete()
        }
    }

    fun delete(path: String) {
        val target = file(path)
        if (target.exists() && !target.delete()) throw RecordFileException("a secret record could not be deleted")
    }

    /** The paths that hold a record, from the file names only. Leftovers of an interrupted write are removed. */
    fun paths(): List<String> {
        val names = directory.list().orEmpty()
        names.filter { it.endsWith(TEMP_SUFFIX) }.forEach { File(directory, it).delete() }
        return names
            .filter { it.endsWith(RECORD_SUFFIX) }
            .map { URLDecoder.decode(it.removeSuffix(RECORD_SUFFIX), "UTF-8") }
    }

    private fun file(path: String) = File(directory, URLEncoder.encode(path, "UTF-8") + RECORD_SUFFIX)

    companion object {
        /** The records of this app, below its `noBackupFilesDir`: Auto Backup and device transfer skip that folder. */
        fun noBackup(context: Context): RecordFiles =
            RecordFiles(File(context.applicationContext.noBackupFilesDir, SECRETS_DIRECTORY))
    }
}
