package com.stainedhead.paperclipmobile.android.diagnostics

import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import java.io.IOException
import java.security.GeneralSecurityException

/** A diagnostic fact the app has seen, or [NotRecorded] when it has not (or cannot know yet). */
sealed interface Recorded<out T> {
    data object NotRecorded : Recorded<Nothing>

    data class Value<T>(
        val value: T,
    ) : Recorded<T>
}

/** Whether the factory's server is a version this app was tested against. Nothing in the shared core reports it yet. */
enum class ServerCompatibility {
    Compatible,
    Incompatible,
}

/** What the last attempt to open the app's data came to; `Unavailable*` names the cause's class, never its text. */
enum class GraphOpenOutcome {
    Opened,
    UnavailableStorage,
    UnavailableKeystore,
    UnavailableOther,
}

/** The class of the last problem with the phone's key storage that a screen reported. */
enum class KeyErrorClass {
    StorageFailed,
    KeyInvalidated,
    AuthDeclined,
    CurrentKeyUnknown,
}

enum class PairingOutcome {
    Approved,
    Cancelled,
    Expired,
    Failed,
}

private val VERSION = Regex("""[0-9A-Za-z.+\-]{1,32}""")

/**
 * What the app knows about itself, as typed values only (AND-018): no address, host, token, key id, company or person,
 * because nothing here can hold one. Every field is an enum, or the app's own version name, which is checked. The text
 * for the clipboard is made only from these values ([toReportText]); nothing is sent anywhere.
 */
data class DiagnosticsReport(
    val appVersion: String,
    val serverCompatibility: Recorded<ServerCompatibility> = Recorded.NotRecorded,
    val graphOpen: Recorded<GraphOpenOutcome> = Recorded.NotRecorded,
    val keystoreError: Recorded<KeyErrorClass> = Recorded.NotRecorded,
    val biometricResult: Recorded<DeviceAuthIssue> = Recorded.NotRecorded,
    val pairing: Recorded<PairingOutcome> = Recorded.NotRecorded,
) {
    init {
        require(VERSION.matches(appVersion)) { "The app version must be a plain version name" }
    }

    /** The report as plain English text for the person to paste where they choose. Fixed labels, enum names only. */
    fun toReportText(): String =
        listOf(
            "Paperclip Mobile diagnostics",
            "App version: $appVersion",
            "Server compatibility: ${serverCompatibility.say()}",
            "Graph open: ${graphOpen.say()}",
            "Key store error: ${keystoreError.say()}",
            "Biometric result: ${biometricResult.say()}",
            "Pairing: ${pairing.say()}",
        ).joinToString("\n")

    private fun <T : Enum<T>> Recorded<T>.say(): String =
        when (this) {
            Recorded.NotRecorded -> "not recorded"
            is Recorded.Value -> value.name
        }
}

private const val STORAGE_EXCEPTION = "StorageUnavailableException"
private const val DATABASE_PACKAGE = "android.database"
private const val MAX_CAUSES = 8

/**
 * The class of a failure to open the graph, from the types in its cause chain. The text of the failure (which can name
 * an address, a file or a key) is never read. `StorageUnavailableException` is matched by name because the persistence
 * module that defines it is not visible to the app.
 */
fun Throwable.toGraphOpenOutcome(): GraphOpenOutcome {
    val chain = generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSES).toList()
    return when {
        chain.any { it is GeneralSecurityException } -> {
            GraphOpenOutcome.UnavailableKeystore
        }

        chain.any { it is IOException || it.javaClass.simpleName == STORAGE_EXCEPTION || it.isDatabaseError() } -> {
            GraphOpenOutcome.UnavailableStorage
        }

        else -> {
            GraphOpenOutcome.UnavailableOther
        }
    }
}

private fun Throwable.isDatabaseError() = javaClass.name.startsWith(DATABASE_PACKAGE)

/** The error keys that are about the phone's key storage; any other error is not a key error. */
fun ErrorKey.toKeyErrorClass(): KeyErrorClass? =
    when (this) {
        ErrorKey.StorageFailed -> KeyErrorClass.StorageFailed
        ErrorKey.KeyInvalidated -> KeyErrorClass.KeyInvalidated
        ErrorKey.AuthDeclined -> KeyErrorClass.AuthDeclined
        ErrorKey.CurrentKeyUnknown -> KeyErrorClass.CurrentKeyUnknown
        else -> null
    }

/** A pairing that has ended without being approved; waiting and not started are not outcomes. */
fun PairingStatus.toPairingOutcome(): PairingOutcome? =
    when (this) {
        PairingStatus.Cancelled -> PairingOutcome.Cancelled
        PairingStatus.Expired -> PairingOutcome.Expired
        PairingStatus.Failed -> PairingOutcome.Failed
        PairingStatus.Idle, PairingStatus.Waiting -> null
    }
