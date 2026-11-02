package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess

internal const val MARKER_SUFFIX = "#protected"
private val MARKER_VALUE = byteArrayOf(1)

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
                    removeMarker(account) && addReplacing(account, bytes, Protection.None)
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

    private fun addReplacing(
        account: String,
        bytes: ByteArray,
        protection: Protection,
    ): Boolean {
        var status = backend.add(account, bytes, protection)
        if (status == errSecDuplicateItem) {
            // Replace, so a changed protection takes effect (an in-place update would keep the old access control).
            if (backend.delete(account) != errSecSuccess) return false
            status = backend.add(account, bytes, protection)
        }
        return status == errSecSuccess
    }

    private fun removeMarker(account: String): Boolean = backend.delete(account + MARKER_SUFFIX).isGoneOrAbsent()
}
