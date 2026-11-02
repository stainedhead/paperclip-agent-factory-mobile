package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AuthGateway

/**
 * The pairing half of [AuthGateway] (research, challenge create and poll: `access.ts:2786-2838`). A challenge starts
 * pending; approval happens in the browser, so the subclass simulates it through [approveInBrowser].
 */
abstract class AuthPairingContract {
    abstract fun create(): AuthGateway

    abstract val server: BaseUri

    /** A token the server accepts as a board key, and one it does not. */
    abstract val validKey: Secret
    abstract val invalidKey: Secret

    /** What the person does in the browser: approves the challenge. */
    abstract suspend fun approveInBrowser(challenge: PairingChallenge)

    /** What the clock does after the 10 minute challenge TTL. */
    abstract suspend fun letExpire(challenge: PairingChallenge)

    suspend fun runAll() {
        aChallengeStartsPendingAndNeverPrintsItsSecrets()
        approvalInTheBrowserShowsOnThePoll()
        anExpiredChallengeShowsAsExpired()
        aValidKeyIsAcceptedAndAWrongOneIsUnauthorized()
        aKeyListedWithAPendingKeyIsOnlyForThatKey()
    }

    private suspend fun newChallenge(gateway: AuthGateway): PairingChallenge =
        gateway.createChallenge(server, "Alex's phone", null).mustSucceed("creating a challenge")

    private suspend fun aChallengeStartsPendingAndNeverPrintsItsSecrets() {
        val gateway = create()
        val challenge = newChallenge(gateway)
        check(challenge.id.isNotBlank()) { "a challenge has an id" }
        check(challenge.suggestedPollIntervalMs > 0) { "a challenge suggests a poll interval" }
        check(!challenge.toString().contains("pcp_")) { "a challenge must not print a token" }
        val status = gateway.pollChallenge(server, challenge.id, challenge.challengeToken).mustSucceed("first poll")
        check(status == ChallengeStatus.Pending) { "a new challenge is pending but was $status" }
    }

    private suspend fun approvalInTheBrowserShowsOnThePoll() {
        val gateway = create()
        val challenge = newChallenge(gateway)
        approveInBrowser(challenge)
        val status = gateway.pollChallenge(server, challenge.id, challenge.challengeToken).mustSucceed("poll")
        check(status == ChallengeStatus.Approved) { "an approved challenge polls as approved but was $status" }
    }

    private suspend fun anExpiredChallengeShowsAsExpired() {
        val gateway = create()
        val challenge = newChallenge(gateway)
        letExpire(challenge)
        val status = gateway.pollChallenge(server, challenge.id, challenge.challengeToken).mustSucceed("poll")
        check(status == ChallengeStatus.Expired) { "a challenge past its TTL polls as expired but was $status" }
    }

    private suspend fun aValidKeyIsAcceptedAndAWrongOneIsUnauthorized() {
        val gateway = create()
        val access = gateway.meWithKey(server, validKey).mustSucceed("me with a valid key")
        check(access.profile.userId.isNotBlank()) { "me names the signed-in person" }
        gateway.meWithKey(server, invalidKey).mustFailWith(DomainError.Unauthorized, "me with a wrong key")
    }

    private suspend fun aKeyListedWithAPendingKeyIsOnlyForThatKey() {
        val gateway = create()
        val keys = gateway.listBoardKeysWithKey(server, validKey).mustSucceed("keys with a valid pending key")
        val keyId = gateway.meWithKey(server, validKey).mustSucceed("me with a valid key").keyId
        check(keyId == null || keys.any { it.id == keyId }) { "the pending key is among the keys the server lists" }
        gateway.listBoardKeysWithKey(server, invalidKey).mustFailWith(DomainError.Unauthorized, "keys with a wrong key")
    }
}

/**
 * The signed-in half of [AuthGateway]: me, companies, board keys and revocation (research, `access.ts:2885-2980`).
 * [instance] is a paired factory whose key is the one in [currentKeyId] (listed with at least one other key).
 */
abstract class AuthAccountContract {
    abstract fun create(): AuthGateway

    abstract val instance: InstanceId

    /** A factory the device holds no usable key for. */
    abstract val signedOutInstance: InstanceId

    suspend fun runAll() {
        meNamesThePersonAndEveryCompanyHasAMembership()
        aSignedOutFactoryIsUnauthorized()
        revokingAListedKeyRemovesItAndAnUnknownKeyIsNotFound()
        revokingTheCurrentKeyMakesTheNextCallUnauthorized()
    }

    private suspend fun meNamesThePersonAndEveryCompanyHasAMembership() {
        val gateway = create()
        val access = gateway.me(instance).mustSucceed("me")
        val companies = gateway.listCompanies(instance).mustSucceed("companies")
        check(companies.isNotEmpty()) { "the key opens at least one company" }
        check(access.memberships.all { m -> companies.any { it.id == m.companyId } }) {
            "every membership names a company the list returns"
        }
    }

    private suspend fun aSignedOutFactoryIsUnauthorized() {
        create().me(signedOutInstance).mustFailWith(DomainError.Unauthorized, "me without a usable key")
    }

    private suspend fun revokingAListedKeyRemovesItAndAnUnknownKeyIsNotFound() {
        val gateway = create()
        val keys = gateway.listBoardKeys(instance).mustSucceed("keys")
        val other = keys.first { !it.isCurrentDevice && it.id != gateway.me(instance).mustSucceed("me").keyId }
        gateway.revokeBoardKey(instance, other.id).mustSucceed("revoking another key")
        check(gateway.listBoardKeys(instance).mustSucceed("keys").none { it.id == other.id }) {
            "a revoked key leaves the list"
        }
        gateway.revokeBoardKey(instance, "no-such-key").mustFailWith(DomainError.NotFound, "revoking an unknown key")
    }

    private suspend fun revokingTheCurrentKeyMakesTheNextCallUnauthorized() {
        val gateway = create()
        gateway.revokeCurrentKey(instance).mustSucceed("revoking the current key")
        gateway.me(instance).mustFailWith(DomainError.Unauthorized, "me after the key was revoked")
    }
}
