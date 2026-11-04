package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyMembership
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.UserProfile
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Fixtures that match the design mocks. Every token is obviously fake. */
object TestData {
    const val TOKEN = "pcp_board_TESTONLY0000000000000001"
    const val HOME_URI = "https://home.example.com"
    const val KEY_ID = "key-current"
    val acme = CompanyId("company-acme")
    val globex = CompanyId("company-globex")
    val epoch: Instant =
        Instant.fromEpochSeconds(
            com.stainedhead.paperclipmobile.testing.fakes.FakeClock.START_EPOCH_SECONDS,
        )

    fun uri(text: String = HOME_URI): BaseUri = (BaseUri.parse(text) as BaseUriResult.Valid).uri

    fun instance(
        id: String = "inst-1",
        name: String = "Home factory",
        uri: String = HOME_URI,
        sortOrder: Int = 0,
        status: InstanceStatus = InstanceStatus.SignedIn,
    ) = Instance(
        id = InstanceId(id),
        displayName = name,
        baseUri = uri(uri),
        deploymentMode = DeploymentMode.Authenticated,
        exposure = Exposure.Private,
        authMethod = AuthMethod.PairedBoardKey,
        boardKeyId = KEY_ID,
        keyExpiresAt = epoch + 30.days,
        defaultCompanyId = acme,
        lastCompanyId = null,
        pins = emptyList(),
        createdAt = epoch,
        lastConnectedAt = epoch,
        sortOrder = sortOrder,
        status = status,
    )

    fun access(
        role: Role = Role.Owner,
        keyId: String? = KEY_ID,
    ) = BoardAccess(
        profile = UserProfile("user-1", "Alex Operator", "alex@example.com", isInstanceAdmin = false),
        keyId = keyId,
        memberships = listOf(CompanyMembership(acme, role), CompanyMembership(globex, Role.Viewer)),
    )

    fun companyRefs() = listOf(CompanyRef(acme, "Acme Labs"), CompanyRef(globex, "Globex"))

    fun boardKeys() =
        listOf(
            BoardKey(KEY_ID, "Alex's iPhone", epoch, epoch, epoch + 30.days, null),
            BoardKey("key-laptop", "paperclipai cli", epoch, null, null, null),
        )

    fun challenge(
        expiresIn: kotlin.time.Duration = 10.minutes,
        pollMs: Long = 1_000,
        from: Instant = epoch,
    ) = PairingChallenge(
        id = "challenge-1",
        challengeToken = Secret.of("challenge-TESTONLY-secret"),
        pendingBoardToken = Secret.of(TOKEN),
        approvalUrl = Secret.of("$HOME_URI/cli-auth/challenge-1?token=challenge-TESTONLY-secret"),
        expiresAt = from + expiresIn,
        suggestedPollIntervalMs = pollMs,
    )
}
