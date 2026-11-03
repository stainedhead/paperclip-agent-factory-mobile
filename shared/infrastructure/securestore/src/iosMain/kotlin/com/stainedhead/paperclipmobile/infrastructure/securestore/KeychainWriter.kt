package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess

internal const val MARKER_SUFFIX = "#protected"
internal const val REPLACEMENT_SUFFIX = "#new"
private val MARKER_VALUE = byteArrayOf(1)
private const val REPLACE_ATTEMPTS = 2

internal fun Int.isGoneOrAbsent(): Boolean = this == errSecSuccess || this == errSecItemNotFound

/** The write side of the store: protection choice, replace-on-duplicate and the invalidation marker. */
internal class KeychainWriter(
    private val backend: KeychainBackend,
) {
    fun put(
        account: String,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult {
        // The String copy made by reveal() cannot be wiped; the byte copies made here are.
        val bytes = value.reveal().encodeToByteArray()
        try {
            val stored =
                if (policy.requireUserPresence) {
                    addProtected(account, bytes)
                } else {
                    addReplacing(account, bytes, Protection.None) && removeMarker(account)
                }
            return if (stored) SecretWriteResult.Stored else SecretWriteResult.Failed
        } finally {
            bytes.fill(0)
        }
    }

    fun remove(account: String) {
        val status = backend.delete(account)
        check(status.isGoneOrAbsent()) { "the keychain refused to delete an item (status $status)" }
    }

    private fun addProtected(
        account: String,
        bytes: ByteArray,
    ): Boolean {
        listOf(Protection.BiometryCurrentSet, Protection.UserPresence).firstOrNull {
            addReplacing(account, bytes, it)
        } ?: return false
        if (addReplacing(account + MARKER_SUFFIX, MARKER_VALUE.copyOf(), Protection.None)) return true
        remove(account) // without its marker an invalidation could not be told from "never stored"
        return false
    }

    /**
     * Adds the item, replacing one that exists. A replacement is not an in-place update (that would keep the old access
     * control), so the new value is first added under a spare account: if that fails, or the old item cannot be
     * deleted, the old secret is untouched and still reads as itself. Only the short step between deleting the old item
     * and adding the new one under its own name can lose it; that add is tried twice and the spare never survives
     * (review1b F-B-3).
     */
    private fun addReplacing(
        account: String,
        bytes: ByteArray,
        protection: Protection,
    ): Boolean {
        val status = backend.add(account, bytes, protection)
        if (status != errSecDuplicateItem) return status == errSecSuccess
        val spare = account + REPLACEMENT_SUFFIX
        // A spare left by an earlier replacement that was killed half way is stale: clear it, then add the new one.
        val replaced =
            backend.delete(spare).isGoneOrAbsent() &&
                backend.add(spare, bytes, protection) == errSecSuccess &&
                backend.delete(account) == errSecSuccess &&
                (0 until REPLACE_ATTEMPTS).any { backend.add(account, bytes, protection) == errSecSuccess }
        backend.delete(spare)
        return replaced
    }

    private fun removeMarker(account: String): Boolean = backend.delete(account + MARKER_SUFFIX).isGoneOrAbsent()
}
