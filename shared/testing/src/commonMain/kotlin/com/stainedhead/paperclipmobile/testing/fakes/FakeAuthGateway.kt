package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.testing.TestData

/**
 * A scripted authentication endpoint. Set the public `...Result` properties to script a call, and read [calls] to
 * assert which endpoints were (not) reached. [pollResults] are consumed in order; the last one repeats.
 *
 * It also keeps the server's key state so the contract suite holds (research, board keys): revoking the current key
 * makes `me` answer 401, revoking a listed key removes it and an unknown key id is a 404, and when [validKeys] is set
 * only those tokens are accepted by `meWithKey`.
 */
class FakeAuthGateway :
    AuthCallLog(),
    AuthGateway {
    /** Resolves the host a stored-key call (`me`, `listBoardKeys`, ...) reaches, as an adapter reads the repository. */
    var hostOf: (InstanceId) -> BaseUri? = { null }

    /** Resolves the token a stored-key call sends, as a real adapter reads the secret store. */
    var tokenOf: (InstanceId) -> String? = { null }

    var createResult: DomainResult<PairingChallenge>? = null
    val pollResults = ArrayDeque<DomainResult<ChallengeStatus>>()
    var meResult: DomainResult<BoardAccess> = DomainResult.Success(TestData.access())
    var meWithKeyResult: DomainResult<BoardAccess> = DomainResult.Success(TestData.access())
    var companiesResult: DomainResult<List<CompanyRef>> = DomainResult.Success(TestData.companyRefs())
    var keysResult: DomainResult<List<BoardKey>> = DomainResult.Success(TestData.boardKeys())
    var revokeKeyResult: DomainResult<Unit> = DomainResult.Success(Unit)
    var revokeCurrentResult: DomainResult<Unit> = DomainResult.Success(Unit)

    /** When not null, `meWithKey` accepts only these tokens and answers 401 for any other. */
    var validKeys: Set<String>? = null

    /** Factories the device holds no usable key for: `me` answers 401 for them. */
    val signedOutInstances = mutableSetOf<InstanceId>()
    private val challengeServers = mutableMapOf<String, BaseUri>()
    private val pendingKeyServers = mutableMapOf<String, BaseUri>()
    private val revokedKeyIds = mutableSetOf<String>()
    private val revokedInstances = mutableSetOf<InstanceId>()

    /** The pending token handed out by the last created challenge, so tests can check it was wiped. */
    var lastPendingToken: Secret? = null
        private set

    override suspend fun createChallenge(
        baseUri: BaseUri,
        clientName: String,
        requestedCompanyId: CompanyId?,
    ): DomainResult<PairingChallenge> {
        record("createChallenge", baseUri)
        val result = createResult ?: DomainResult.Success(TestData.challenge())
        val created = (result as? DomainResult.Success)?.value
        lastPendingToken = created?.pendingBoardToken
        if (created != null) {
            challengeServers[created.id] = baseUri
            pendingKeyServers[created.pendingBoardToken.reveal()] = baseUri
        }
        return result
    }

    override suspend fun pollChallenge(
        baseUri: BaseUri,
        challengeId: String,
        challengeToken: Secret,
    ): DomainResult<ChallengeStatus> {
        record("pollChallenge", baseUri, challengeToken.reveal())
        if (challengeServers[challengeId]?.let { it != baseUri } ==
            true
        ) {
            return DomainResult.Failure(DomainError.NotFound)
        }
        val next = if (pollResults.size > 1) pollResults.removeFirst() else pollResults.firstOrNull()
        return next ?: DomainResult.Success(ChallengeStatus.Pending)
    }

    override suspend fun meWithKey(
        baseUri: BaseUri,
        key: Secret,
    ): DomainResult<BoardAccess> {
        record("meWithKey", baseUri, key.reveal())
        if (isIssuedElsewhere(baseUri, key)) return DomainResult.Failure(DomainError.Unauthorized)
        val accepted = validKeys
        if (accepted != null && key.reveal() !in accepted) return DomainResult.Failure(DomainError.Unauthorized)
        return meWithKeyResult
    }

    /** A key is a row in the issuing server's table: another server answers 401 (a known pending key only). */
    private fun isIssuedElsewhere(
        baseUri: BaseUri,
        key: Secret,
    ): Boolean = pendingKeyServers[key.reveal()]?.let { it != baseUri } == true

    override suspend fun me(instanceId: InstanceId): DomainResult<BoardAccess> {
        record("me", hostOf(instanceId), tokenOf(instanceId))
        if (instanceId in revokedInstances ||
            instanceId in signedOutInstances
        ) {
            return DomainResult.Failure(DomainError.Unauthorized)
        }
        return meResult
    }

    override suspend fun listCompanies(instanceId: InstanceId): DomainResult<List<CompanyRef>> {
        record("listCompanies", hostOf(instanceId), tokenOf(instanceId))
        return companiesResult
    }

    override suspend fun listBoardKeys(instanceId: InstanceId): DomainResult<List<BoardKey>> {
        record("listBoardKeys", hostOf(instanceId), tokenOf(instanceId))
        val keys = keysResult
        return if (keys is DomainResult.Success) {
            DomainResult.Success(keys.value.filter { it.id !in revokedKeyIds })
        } else {
            keys
        }
    }

    override suspend fun listBoardKeysWithKey(
        baseUri: BaseUri,
        key: Secret,
    ): DomainResult<List<BoardKey>> {
        record("listBoardKeysWithKey", baseUri, key.reveal())
        if (isIssuedElsewhere(baseUri, key)) return DomainResult.Failure(DomainError.Unauthorized)
        val accepted = validKeys
        if (accepted != null && key.reveal() !in accepted) return DomainResult.Failure(DomainError.Unauthorized)
        val keys = keysResult
        return if (keys is DomainResult.Success) {
            DomainResult.Success(keys.value.filter { it.id !in revokedKeyIds })
        } else {
            keys
        }
    }

    override suspend fun revokeBoardKey(
        instanceId: InstanceId,
        keyId: String,
    ): DomainResult<Unit> {
        record("revokeBoardKey", hostOf(instanceId), tokenOf(instanceId))
        val keys = (keysResult as? DomainResult.Success)?.value
        if (revokeKeyResult is DomainResult.Success && keys != null) {
            if (keys.none { it.id == keyId && it.id !in revokedKeyIds }) {
                return DomainResult.Failure(
                    DomainError.NotFound,
                )
            }
            revokedKeyIds += keyId
        }
        return revokeKeyResult
    }

    override suspend fun revokeCurrentKey(instanceId: InstanceId): DomainResult<Unit> {
        record("revokeCurrentKey", hostOf(instanceId), tokenOf(instanceId))
        if (revokeCurrentResult is DomainResult.Success) revokedInstances += instanceId
        return revokeCurrentResult
    }
}

/** One call that reached (or would reach) [host], with the token it sent. [token] is a fake test token. */
data class AuthenticatedCall(
    val name: String,
    val host: BaseUri?,
    val token: String?,
)

/** The call counters and the credential-bearing call log of [FakeAuthGateway]. */
abstract class AuthCallLog {
    val calls = mutableMapOf<String, Int>()

    /**
     * Every call that carries or resolves a credential, in order, with the host it would reach and the token it would
     * send. Tests assert that no such call reaches a host other than the one that issued the key.
     */
    val authenticatedCalls = mutableListOf<AuthenticatedCall>()

    /** Runs while a call is "in flight", before its result is returned, so a test can edit state mid-request. */
    var onCall: (suspend (AuthenticatedCall) -> Unit)? = null

    fun count(name: String): Int = calls[name] ?: 0

    protected suspend fun record(
        name: String,
        host: BaseUri? = null,
        token: String? = null,
    ) {
        calls[name] = count(name) + 1
        val call = AuthenticatedCall(name, host, token)
        authenticatedCalls += call
        onCall?.invoke(call)
    }
}
