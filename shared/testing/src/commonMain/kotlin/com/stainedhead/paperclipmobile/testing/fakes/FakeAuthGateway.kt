package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.testing.TestData

/**
 * A scripted authentication endpoint. Set the public `...Result` properties to script a call, and read [calls] to
 * assert which endpoints were (not) reached. [pollResults] are consumed in order; the last one repeats.
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
        return meWithKeyResult
    }

    override suspend fun me(instanceId: InstanceId): DomainResult<BoardAccess> {
        record("me")
        return meResult
    }

    override suspend fun listCompanies(instanceId: InstanceId): DomainResult<List<CompanyRef>> {
        record("listCompanies")
        return companiesResult
    }

    override suspend fun listBoardKeys(instanceId: InstanceId): DomainResult<List<BoardKey>> {
        record("listBoardKeys")
        return keysResult
    }

    override suspend fun revokeBoardKey(
        instanceId: InstanceId,
        keyId: String,
    ): DomainResult<Unit> {
        record("revokeBoardKey")
        return revokeKeyResult
    }

    override suspend fun revokeCurrentKey(instanceId: InstanceId): DomainResult<Unit> {
        record("revokeCurrentKey")
        return revokeCurrentResult
    }
}
