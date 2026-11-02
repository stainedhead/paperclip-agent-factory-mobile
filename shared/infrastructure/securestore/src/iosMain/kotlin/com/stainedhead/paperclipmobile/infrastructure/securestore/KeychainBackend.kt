package com.stainedhead.paperclipmobile.infrastructure.securestore

import platform.Security.errSecItemNotFound

/** How the Keychain protects one item at rest. [None] still means locked-device-only and this-device-only. */
internal enum class Protection {
    None,
    BiometryCurrentSet,
    UserPresence,
}

internal class ReadOutcome(
    val status: Int,
    val data: ByteArray?,
)

internal class AccountsOutcome(
    val status: Int,
    val accounts: List<String>,
)

/**
 * The few Keychain operations the store needs, one call each. The real implementation talks to Security.framework;
 * tests script a fake. Every call returns an `OSStatus`; `0` is success.
 */
internal interface KeychainBackend {
    /** Adds one generic password item. A buffer passed in is the caller's to wipe afterwards. */
    fun add(
        account: String,
        data: ByteArray,
        protection: Protection,
    ): Int

    /** Reads the value, prompting the person with [reason] when the item is protected. */
    fun read(
        account: String,
        reason: String,
    ): ReadOutcome

    /** Whether the item exists, from its attributes only: no value is read and no prompt is shown. */
    fun exists(account: String): Int

    fun delete(account: String): Int

    /** The accounts that start with [prefix], from attributes only: no value is read and no prompt is shown. */
    fun accounts(prefix: String): AccountsOutcome
}

internal enum class ReadFailure {
    NotFound,
    Unreadable,
}

/**
 * A failed read is either "there is no such item" or "it could not be read now" (device locked, prompt cancelled or
 * failed, any other error). Only the first may become Missing: the second must never look like a missing or
 * invalidated key, because those make the app ask the person to pair again (PRD FR-015).
 */
internal fun mapReadFailure(status: Int): ReadFailure =
    if (status == errSecItemNotFound) ReadFailure.NotFound else ReadFailure.Unreadable
