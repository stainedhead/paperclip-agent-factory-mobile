package com.stainedhead.paperclipmobile.domain.port

import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/** The only source of time. Tests use a fake so day boundaries and timeouts are deterministic. */
interface Clock {
    fun now(): Instant
}

/** Creates ids for things the app owns, such as a factory. */
interface IdGenerator {
    fun generate(): String
}

/** Non-secret factory configuration, plus the cache that belongs to each factory (PRD FR-002). */
interface InstanceRepository {
    fun observeAll(): Flow<List<Instance>>

    suspend fun get(id: InstanceId): Instance?

    suspend fun add(instance: Instance)

    suspend fun update(instance: Instance)

    /** Sets `sortOrder` to the position in [orderedIds]. */
    suspend fun reorder(orderedIds: List<InstanceId>)

    /** Deletes the configuration row and every cached entity of this factory. */
    suspend fun delete(id: InstanceId)
}

/** The device-wide preferences (PRD FR-017). */
interface AppSettingsStore {
    fun observe(): Flow<AppSettings>

    suspend fun save(settings: AppSettings)
}

/** Where credentials live: the Keychain or the Keystore (PRD FR-010). */
interface SecretStore {
    suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult

    suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult

    suspend fun delete(key: SecretKey)

    /** Deletes every secret under `instance/{instanceId}/`. */
    suspend fun deleteNamespace(instanceId: InstanceId)
}

/** Device owner authentication (Face ID, fingerprint, device PIN). */
interface Biometrics {
    suspend fun authenticate(reason: AuthReason): BiometricResult
}

enum class AuthReason {
    UnlockApp,
    ConfirmDestructiveAction,
}

enum class BiometricResult {
    Approved,
    Cancelled,
    Failed,
    LockedOut,
    Unavailable,
}

/** Whether the phone can reach the network at all. */
interface Connectivity {
    val isOnline: StateFlow<Boolean>
}

/** Opens the approval page in a system browser session (ASWebAuthenticationSession or a Custom Tab). */
interface PairingBrowser {
    suspend fun open(approvalUrl: Secret)
}

/** `GET {baseUri}/api/health`, unauthenticated. */
interface HealthGateway {
    suspend fun probe(baseUri: BaseUri): DomainResult<HealthReport>
}

/** The authentication endpoints of one Paperclip instance (PRD FR-020 to FR-025). */
interface AuthGateway {
    suspend fun createChallenge(
        baseUri: BaseUri,
        clientName: String,
        requestedCompanyId: CompanyId?,
    ): DomainResult<PairingChallenge>

    suspend fun pollChallenge(
        baseUri: BaseUri,
        challengeId: String,
        challengeToken: Secret,
    ): DomainResult<ChallengeStatus>

    /** `GET /api/cli-auth/me` with a key that is not stored yet. */
    suspend fun meWithKey(
        baseUri: BaseUri,
        key: Secret,
    ): DomainResult<BoardAccess>

    /** `GET /api/cli-auth/me` with the stored key of [instanceId]. */
    suspend fun me(instanceId: InstanceId): DomainResult<BoardAccess>

    /** `GET /api/companies?scope=accessible` with the stored key. */
    suspend fun listCompanies(instanceId: InstanceId): DomainResult<List<CompanyRef>>

    suspend fun listBoardKeys(instanceId: InstanceId): DomainResult<List<BoardKey>>

    suspend fun revokeBoardKey(
        instanceId: InstanceId,
        keyId: String,
    ): DomainResult<Unit>

    /** `POST /api/cli-auth/revoke-current`. */
    suspend fun revokeCurrentKey(instanceId: InstanceId): DomainResult<Unit>
}
