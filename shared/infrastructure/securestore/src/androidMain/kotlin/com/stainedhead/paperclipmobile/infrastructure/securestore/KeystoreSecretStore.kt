package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.content.Context
import com.stainedhead.paperclipmobile.domain.AttestedReadResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

private const val NAMESPACE_ROOT = "instance/"

/**
 * [SecretStore] on the Android Keystore (AND-001). Every secret is one record file in `noBackupFilesDir`: the value
 * encrypted with AES-256-GCM under a Keystore key that never leaves the secure hardware. The path of the secret is the
 * associated data, so a record copied under another name does not decrypt.
 *
 * A secret that must be protected (FR-011) is sealed under a key that demands the person's authentication; the store
 * asks `authenticate` (device owner authentication, supplied by the app) with a cipher initialised for this one
 * use, and completes the operation with the cipher the prompt authorised; the key allows nothing else (per-use
 * authorisation, no window of seconds, R-001). A refusal reads as [SecretReadResult.Denied] and writes as
 * [SecretWriteResult.Failed]; there is no fallback to an unprotected key.
 *
 * A read tells apart: a key the system invalidated ([SecretReadResult.Invalidated], the factory needs pairing again),
 * a record or key that is gone or does not decrypt ([SecretReadResult.Missing], never plaintext), and a key that cannot
 * be used right now ([SecretReadResult.Denied], changing nothing). A failed write leaves the old record in place. A
 * delete the system refuses throws, like the fake, so a removed factory is never left holding its key unnoticed.
 */
class KeystoreSecretStore internal constructor(
    private val backend: KeystoreBackend,
    private val files: RecordFiles,
    private val authenticate: suspend (String, Cipher) -> Cipher?,
    private val writeReason: String,
) : SecretStore {
    /**
     * The Keystore of this app. [authenticate] shows the device owner prompt with the text it is given for the cipher
     * it is given, and answers the cipher the person authorised or `null`; [writeReason] is that text when a
     * protected secret is being stored. [keyAliasPrefix] names the app's keys in the Keystore.
     */
    constructor(
        context: Context,
        authenticate: suspend (String, Cipher) -> Cipher?,
        writeReason: String,
        keyAliasPrefix: String = DEFAULT_KEY_ALIAS_PREFIX,
    ) : this(
        AndroidKeystoreBackend(keyAliasPrefix),
        RecordFiles.noBackup(context),
        authenticate,
        writeReason,
    )

    /** Guards the record files and the keys. Never held while the person is being asked (R-002). */
    private val lock = Mutex()

    /** One prompt at a time: the system shows a single prompt, and a second would cancel the first. */
    private val promptLock = Mutex()

    /** Counts deletions, under [lock]; a write whose prompt overlapped one must not bring the secret back. */
    private var removals = 0L

    companion object {
        /** The prefix of the app's Keystore keys unless the app chooses another. */
        const val DEFAULT_KEY_ALIAS_PREFIX = "pafm.secret/"
    }

    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult = withContext(Dispatchers.IO) { putNow(key.path, value, policy) }

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult = withContext(Dispatchers.IO) { getNow(key.path, reason).result }

    /**
     * Attested only when the Keystore-bound decrypt succeeded under a prompt the person approved for this call
     * (ADR-146). An unprotected record, a missing one and any failure that did not come from the prompt attest nothing;
     * a refused prompt is Denied and an invalidated key is Invalidated.
     */
    override suspend fun readAttested(
        key: SecretKey,
        reason: String,
    ): AttestedReadResult = withContext(Dispatchers.IO) { getNow(key.path, reason).attested() }

    override suspend fun delete(key: SecretKey) =
        withContext(Dispatchers.IO) {
            removeLocked { removeNow(files, backend, key.path) }
        }

    override suspend fun deleteNamespace(instanceId: InstanceId) =
        withContext(Dispatchers.IO) {
            removeLocked {
                val prefix = SecretKey.namespacePrefix(instanceId)
                files.paths().filter { it.startsWith(prefix) }.forEach { removeNow(files, backend, it) }
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
            // First step under the lock: either the whole write, or the cipher the person has to authorise.
            val begun =
                lock.withLock {
                    val sealed = backend.seal(alias, bytes, aad, protection)
                    if (sealed is SealOutcome.NeedsAuthentication) {
                        WriteStep.Ask(sealed.cipher, removals)
                    } else {
                        WriteStep.Done(storeSealed(files, backend, path, protection, sealed))
                    }
                }
            if (begun !is WriteStep.Ask) return (begun as WriteStep.Done).result
            // The person is asked with the lock free: every other secret operation carries on meanwhile.
            val authorised =
                promptLock.withLock { authenticate(writeReason, begun.cipher) } ?: return SecretWriteResult.Failed
            return lock.withLock {
                // A delete during the prompt wins: the secret the person meant to protect is gone.
                if (removals != begun.removals) {
                    SecretWriteResult.Failed
                } else {
                    storeSealed(files, backend, path, protection, backend.finishSeal(authorised, bytes, aad))
                }
            }
        } catch (_: RecordFileException) {
            return SecretWriteResult.Failed
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun getNow(
        path: String,
        reason: String,
    ): ReadAttempt {
        val aad = path.encodeToByteArray()
        val begun = lock.withLock { beginRead(files, backend, path, aad) }
        if (begun !is ReadStep.Ask) return ReadAttempt((begun as ReadStep.Done).result, prompted = false)
        // The person is asked with the lock free: every other secret operation carries on meanwhile.
        val authorised =
            promptLock.withLock { authenticate(reason, begun.cipher) }
                ?: return ReadAttempt(SecretReadResult.Denied, prompted = true)
        val result =
            lock.withLock {
                val now =
                    try {
                        files.read(path)
                    } catch (_: RecordFileException) {
                        return@withLock SecretReadResult.Denied
                    }
                val record = now?.let(SecretRecord::decode)
                when {
                    now == null || record == null -> SecretReadResult.Missing

                    // Replaced while the prompt was open: the person approved the old secret, not this one. Ask again.
                    !now.contentEquals(begun.stored) -> SecretReadResult.Denied

                    else -> readResult(backend.finishOpen(authorised, record.ciphertext, aad))
                }
            }
        return ReadAttempt(result, prompted = true)
    }

    private suspend fun removeLocked(block: () -> Unit) =
        lock.withLock {
            removals++
            block()
        }

    override fun toString(): String = "KeystoreSecretStore"
}

private fun beginRead(
    files: RecordFiles,
    backend: KeystoreBackend,
    path: String,
    aad: ByteArray,
): ReadStep {
    val stored =
        try {
            files.read(path)
        } catch (_: RecordFileException) {
            return ReadStep.Done(SecretReadResult.Denied)
        } ?: return ReadStep.Done(SecretReadResult.Missing)
    val record = SecretRecord.decode(stored) ?: return ReadStep.Done(SecretReadResult.Missing)
    val outcome = backend.open(aliasFor(path, record.protection), record.iv, record.ciphertext, aad)
    return if (outcome is OpenOutcome.NeedsAuthentication) {
        ReadStep.Ask(outcome.cipher, stored)
    } else {
        ReadStep.Done(readResult(outcome))
    }
}

/** A read's result and whether the person was asked for it: only a read that asked can attest (ADR-146). */
private class ReadAttempt(
    val result: SecretReadResult,
    val prompted: Boolean,
)

/** Reads the attempt as an attestation: only a read that asked the person and then decrypted attests (ADR-146). */
private fun ReadAttempt.attested(): AttestedReadResult =
    when (result) {
        is SecretReadResult.Found -> {
            if (prompted) {
                AttestedReadResult.Authenticated(result.secret)
            } else {
                result.secret.wipe()
                AttestedReadResult.NotAttested
            }
        }

        SecretReadResult.Denied -> {
            if (prompted) AttestedReadResult.Denied else AttestedReadResult.NotAttested
        }

        SecretReadResult.Invalidated -> {
            AttestedReadResult.Invalidated
        }

        SecretReadResult.Missing -> {
            AttestedReadResult.NotAttested
        }
    }

/** What the first, locked step of a write found: nothing left to ask, or a cipher for the person to authorise. */
private sealed interface WriteStep {
    class Done(
        val result: SecretWriteResult,
    ) : WriteStep

    class Ask(
        val cipher: Cipher,
        val removals: Long,
    ) : WriteStep
}

/** The same for a read; [Ask] remembers the record bytes the cipher belongs to. */
private sealed interface ReadStep {
    class Done(
        val result: SecretReadResult,
    ) : ReadStep

    class Ask(
        val cipher: Cipher,
        val stored: ByteArray,
    ) : ReadStep
}

private fun storeSealed(
    files: RecordFiles,
    backend: KeystoreBackend,
    path: String,
    protection: Boolean,
    sealed: SealOutcome,
): SecretWriteResult {
    if (sealed !is SealOutcome.Sealed) return SecretWriteResult.Failed
    files.write(path, SecretRecord(protection, sealed.iv, sealed.ciphertext).encode())
    // The record now names this key; the key of the other mode is dead weight (best effort).
    runCatching { backend.deleteKey(aliasFor(path, !protection)) }
    return SecretWriteResult.Stored
}

private fun removeNow(
    files: RecordFiles,
    backend: KeystoreBackend,
    path: String,
) {
    // The record first: if a key cannot be deleted afterwards, nothing readable is left behind.
    files.delete(path)
    backend.deleteKey(aliasFor(path, true))
    backend.deleteKey(aliasFor(path, false))
}

private fun readResult(outcome: OpenOutcome): SecretReadResult =
    when (outcome) {
        is OpenOutcome.Opened -> found(outcome.plaintext)
        OpenOutcome.KeyMissing, OpenOutcome.Undecryptable -> SecretReadResult.Missing
        OpenOutcome.Invalidated -> SecretReadResult.Invalidated
        is OpenOutcome.NeedsAuthentication, OpenOutcome.Unreadable -> SecretReadResult.Denied
    }

private fun found(plaintext: ByteArray): SecretReadResult {
    val secret = Secret.of(plaintext.decodeToString())
    plaintext.fill(0)
    return SecretReadResult.Found(secret)
}

/** One key per secret and mode, so changing the protection never touches the key the current record depends on. */
private fun aliasFor(
    path: String,
    protection: Boolean,
): String = path + if (protection) ":p" else ":u"
