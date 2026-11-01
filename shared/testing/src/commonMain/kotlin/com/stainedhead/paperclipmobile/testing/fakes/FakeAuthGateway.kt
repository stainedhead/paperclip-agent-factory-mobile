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
class FakeAuthGateway : AuthGateway {
    val calls = mutableMapOf<String, Int>()

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
    private val revokedKeyIds = mutableSetOf<String>()
    private val revokedInstances = mutableSetOf<InstanceId>()

    /** The pending token handed out by the last created challenge, so tests can check it was wiped. */
    var lastPendingToken: Secret? = null
        private set

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String) {
        calls[name] = count(name) + 1
    }

    override suspend fun createChallenge(
        baseUri: BaseUri,
        clientName: String,
        requestedCompanyId: CompanyId?,
    ): DomainResult<PairingChallenge> {
        record("createChallenge")
        val result = createResult ?: DomainResult.Success(TestData.challenge())
        lastPendingToken = (result as? DomainResult.Success)?.value?.pendingBoardToken
        return result
    }

    override suspend fun pollChallenge(
        baseUri: BaseUri,
        challengeId: String,
        challengeToken: Secret,
    ): DomainResult<ChallengeStatus> {
        record("pollChallenge")
        val next = if (pollResults.size > 1) pollResults.removeFirst() else pollResults.firstOrNull()
        return next ?: DomainResult.Success(ChallengeStatus.Pending)
    }

    override suspend fun meWithKey(
        baseUri: BaseUri,
        key: Secret,
    ): DomainResult<BoardAccess> {
        record("meWithKey")
        val accepted = validKeys
        if (accepted != null && key.reveal() !in accepted) return DomainResult.Failure(DomainError.Unauthorized)
        return meWithKeyResult
    }

    override suspend fun me(instanceId: InstanceId): DomainResult<BoardAccess> {
        record("me")
        if (instanceId in revokedInstances ||
            instanceId in signedOutInstances
        ) {
            return DomainResult.Failure(DomainError.Unauthorized)
        }
        return meResult
    }

    override suspend fun listCompanies(instanceId: InstanceId): DomainResult<List<CompanyRef>> {
        record("listCompanies")
        return companiesResult
    }

    override suspend fun listBoardKeys(instanceId: InstanceId): DomainResult<List<BoardKey>> {
        record("listBoardKeys")
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
        record("revokeBoardKey")
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
        record("revokeCurrentKey")
        if (revokeCurrentResult is DomainResult.Success) revokedInstances += instanceId
        return revokeCurrentResult
    }
}
