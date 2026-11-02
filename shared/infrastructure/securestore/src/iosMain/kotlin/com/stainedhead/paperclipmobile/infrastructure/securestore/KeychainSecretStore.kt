package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess

/**
 * [SecretStore] on the iOS Keychain (AD-030). Items are generic passwords: account = the namespaced key
 * `instance/{id}/{name}`, accessible only while unlocked and never leaving this device. A secret that must be protected
 * (PRD FR-011) is stored behind the current biometric set, or behind user presence when no biometrics are enrolled; it
 * is never stored without protection when protection was asked for.
 *
 * A protected item written with `biometryCurrentSet` disappears when the enrolled biometrics change. So that this can
 * be told from "never stored", a protected write also leaves an unprotected marker item (no secret in it); a protected
 * item gone while its marker remains is [SecretReadResult.Invalidated].
 *
 * Reads that fail for any reason other than "not found" (device locked, prompt cancelled or failed, storage error) are
 * [SecretReadResult.Denied]: they change no state. Failed writes are [SecretWriteResult.Failed]. A delete the Keychain
 * refuses throws, like the fake, so a removed factory is never left holding its key unnoticed. Calls run off the
 * caller's thread because a protected read blocks while the prompt is up.
 */
class KeychainSecretStore internal constructor(
    private val backend: KeychainBackend,
) : SecretStore {
    /** The Keychain of this app under the Keychain service [service]. */
    constructor(service: String) : this(SecurityKeychain(service))

    private val writer = KeychainWriter(backend)

    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult = withContext(Dispatchers.Default) { writer.put(key.path, value, policy) }

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult = withContext(Dispatchers.Default) { getNow(key.path, reason) }

    override suspend fun delete(key: SecretKey) = withContext(Dispatchers.Default) { deleteNow(key.path) }

    override suspend fun deleteNamespace(instanceId: InstanceId) =
        withContext(Dispatchers.Default) {
            val listed = backend.accounts(SecretKey.namespacePrefix(instanceId))
            check(listed.status == errSecSuccess || listed.status == errSecItemNotFound) {
                "the keychain could not list the items to delete (status ${listed.status})"
            }
            // Markers are listed with the rest, so each account is removed on its own.
            listed.accounts.forEach { writer.remove(it) }
        }

    private fun getNow(
        account: String,
        reason: String,
    ): SecretReadResult {
        val outcome = backend.read(account, reason)
        val data = outcome.data
        if (outcome.status == errSecSuccess && data != null) {
            val secret = Secret.of(data.decodeToString())
            data.fill(0)
            return SecretReadResult.Found(secret)
        }
        if (mapReadFailure(outcome.status) == ReadFailure.Unreadable) return SecretReadResult.Denied
        return when (backend.exists(account + MARKER_SUFFIX)) {
            errSecSuccess -> SecretReadResult.Invalidated
            errSecItemNotFound -> SecretReadResult.Missing
            else -> SecretReadResult.Denied
        }
    }

    private fun deleteNow(account: String) {
        writer.remove(account)
        writer.remove(account + MARKER_SUFFIX)
    }

    override fun toString(): String = "KeychainSecretStore"
}
