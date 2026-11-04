package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.content.Context
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val NAMESPACE_ROOT = "instance/"

/**
 * [SecretStore] on the Android Keystore (AND-001). Every secret is one record file in `noBackupFilesDir`: the value
 * encrypted with AES-256-GCM under a Keystore key that never leaves the secure hardware. The path of the secret is the
 * associated data, so a record copied under another name does not decrypt.
 *
 * A secret that must be protected (FR-011) is sealed under a key that demands the person's authentication; the store
 * asks `authenticate` (device owner authentication, supplied by the app) and then uses the key within the window the
 * Keystore grants. A refusal reads as [SecretReadResult.Denied] and writes as [SecretWriteResult.Failed]; there is no
 * fallback to an unprotected key.
 *
 * A read tells apart: a key the system invalidated ([SecretReadResult.Invalidated], the factory needs pairing again),
 * a record or key that is gone or does not decrypt ([SecretReadResult.Missing], never plaintext), and a key that cannot
 * be used right now ([SecretReadResult.Denied], changing nothing). A failed write leaves the old record in place. A
 * delete the system refuses throws, like the fake, so a removed factory is never left holding its key unnoticed.
 */
class KeystoreSecretStore internal constructor(
    private val backend: KeystoreBackend,
    private val files: RecordFiles,
    private val authenticate: suspend (String) -> BiometricResult,
    private val writeReason: String,
) : SecretStore {
    /**
     * The Keystore of this app. [authenticate] shows the device owner prompt with the text it is given and reports what
     * the person did; [writeReason] is that text when a protected secret is being stored. [keyAliasPrefix] names the
     * app's keys in the Keystore.
     */
    constructor(
        context: Context,
        authenticate: suspend (String) -> BiometricResult,
        writeReason: String,
        keyAliasPrefix: String = DEFAULT_KEY_ALIAS_PREFIX,
    ) : this(
        AndroidKeystoreBackend(keyAliasPrefix),
        RecordFiles.noBackup(context),
        authenticate,
        writeReason,
    )

    private val lock = Mutex()

    companion object {
        /** The prefix of the app's Keystore keys unless the app chooses another. */
        const val DEFAULT_KEY_ALIAS_PREFIX = "pafm.secret/"
    }

    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult = withContext(Dispatchers.IO) { lock.withLock { putNow(key.path, value, policy) } }

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult = withContext(Dispatchers.IO) { lock.withLock { getNow(key.path, reason) } }

    override suspend fun delete(key: SecretKey) = withContext(Dispatchers.IO) { lock.withLock { removeNow(key.path) } }

    override suspend fun deleteNamespace(instanceId: InstanceId) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val prefix = SecretKey.namespacePrefix(instanceId)
                files.paths().filter { it.startsWith(prefix) }.forEach { removeNow(it) }
                // A key whose record is already gone must not outlive its factory either.
                backend.aliases(prefix).forEach { backend.deleteKey(it) }
            }
        }

    override suspend fun namespaces(): Set<InstanceId> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                files
                    .paths()
                    .filter { it.startsWith(NAMESPACE_ROOT) }
                    .mapNotNull { it.removePrefix(NAMESPACE_ROOT).substringBefore('/', "").takeIf(String::isNotEmpty) }
                    .map { InstanceId(it) }
                    .toSet()
            }
        }

    private suspend fun putNow(
        path: String,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult {
        // The String copy made by reveal() cannot be wiped; the byte copy made here is.
        val bytes = value.reveal().encodeToByteArray()
        val protection = policy.requireUserPresence
        try {
            val alias = aliasFor(path, protection)
            val aad = path.encodeToByteArray()
            var sealed = backend.seal(alias, bytes, aad, protection)
            if (sealed is SealOutcome.NeedsAuthentication && authenticate(writeReason) == BiometricResult.Approved) {
                sealed = backend.seal(alias, bytes, aad, protection)
            }
            if (sealed !is SealOutcome.Sealed) return SecretWriteResult.Failed
            files.write(path, SecretRecord(protection, sealed.iv, sealed.ciphertext).encode())
            // The record now names this key; the key of the other mode is dead weight (best effort).
            runCatching { backend.deleteKey(aliasFor(path, !protection)) }
            return SecretWriteResult.Stored
        } catch (_: RecordFileException) {
            return SecretWriteResult.Failed
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun getNow(
        path: String,
        reason: String,
    ): SecretReadResult {
        val stored =
            try {
                files.read(path)
            } catch (_: RecordFileException) {
                return SecretReadResult.Denied
            } ?: return SecretReadResult.Missing
        val record = SecretRecord.decode(stored) ?: return SecretReadResult.Missing
        val alias = aliasFor(path, record.protection)
        val aad = path.encodeToByteArray()
        var outcome = backend.open(alias, record.iv, record.ciphertext, aad)
        if (outcome is OpenOutcome.NeedsAuthentication && authenticate(reason) == BiometricResult.Approved) {
            outcome = backend.open(alias, record.iv, record.ciphertext, aad)
        }
        return when (outcome) {
            is OpenOutcome.Opened -> found(outcome.plaintext)
            OpenOutcome.KeyMissing, OpenOutcome.Undecryptable -> SecretReadResult.Missing
            OpenOutcome.Invalidated -> SecretReadResult.Invalidated
            OpenOutcome.NeedsAuthentication, OpenOutcome.Unreadable -> SecretReadResult.Denied
        }
    }

    private fun found(plaintext: ByteArray): SecretReadResult {
        val secret = Secret.of(plaintext.decodeToString())
        plaintext.fill(0)
        return SecretReadResult.Found(secret)
    }

    private fun removeNow(path: String) {
        // The record first: if a key cannot be deleted afterwards, nothing readable is left behind.
        files.delete(path)
        backend.deleteKey(aliasFor(path, true))
        backend.deleteKey(aliasFor(path, false))
    }

    override fun toString(): String = "KeystoreSecretStore"
}

/** One key per secret and mode, so changing the protection never touches the key the current record depends on. */
private fun aliasFor(
    path: String,
    protection: Boolean,
): String = path + if (protection) ":p" else ":u"
