package com.stainedhead.paperclipmobile.infrastructure.securestore

import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess

/** An in-memory [KeychainBackend] with scriptable failures: the store's decisions are testable without a Keychain. */
internal class FakeKeychainBackend : KeychainBackend {
    class Entry(
        val data: ByteArray,
        val protection: Protection,
    )

    val items = linkedMapOf<String, Entry>()
    val addAttempts = mutableListOf<Pair<String, Protection>>()

    /** The buffers handed to [add] and returned by [read], so a test can check they were wiped afterwards. */
    val buffersHandedOut = mutableListOf<ByteArray>()
    val listedPrefixes = mutableListOf<String>()

    /** Status to answer with instead of working, per operation and account (null account = any). */
    val addFailures = mutableMapOf<Protection, Int>()
    var readFailure: Int? = null
    var existsFailure: Int? = null
    var deleteFailure: Int? = null
    var listFailure: Int? = null

    override fun add(
        account: String,
        data: ByteArray,
        protection: Protection,
    ): Int {
        addAttempts += account to protection
        buffersHandedOut += data
        addFailures[protection]?.let { return it }
        if (account in items) return errSecDuplicateItem
        items[account] = Entry(data.copyOf(), protection)
        return errSecSuccess
    }

    override fun read(
        account: String,
        reason: String,
    ): ReadOutcome {
        readFailure?.let { return ReadOutcome(it, null) }
        val entry = items[account] ?: return ReadOutcome(errSecItemNotFound, null)
        return ReadOutcome(errSecSuccess, entry.data.copyOf().also { buffersHandedOut += it })
    }

    override fun exists(account: String): Int =
        existsFailure ?: if (account in items) errSecSuccess else errSecItemNotFound

    override fun delete(account: String): Int {
        deleteFailure?.let { return it }
        return if (items.remove(account) != null) errSecSuccess else errSecItemNotFound
    }

    override fun accounts(prefix: String): AccountsOutcome {
        listedPrefixes += prefix
        listFailure?.let { return AccountsOutcome(it, emptyList()) }
        return AccountsOutcome(errSecSuccess, items.keys.filter { it.startsWith(prefix) })
    }
}
