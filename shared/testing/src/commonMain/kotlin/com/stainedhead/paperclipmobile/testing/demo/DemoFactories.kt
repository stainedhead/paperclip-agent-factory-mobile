package com.stainedhead.paperclipmobile.testing.demo

import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyMembership
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.UserProfile
import com.stainedhead.paperclipmobile.testing.TestData
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The three factories of the Factories mock, the person who is signed in to the first, and its companies. */
object DemoFactories {
    /** Wednesday 2026-11-04T12:00:00Z. A mid-week demo "now" so the Inbox has a "This week" group (ADR-026). */
    val now: Instant = Instant.fromEpochSeconds(NOW_EPOCH_SECONDS)

    /** A fake token: it only proves the Home factory has a stored key, and is never sent anywhere. */
    const val HOME_TOKEN = "pcp_board_TESTONLY0000000000DEMOHOME"
    const val HOME_ADDRESS = "https://factory.tail1234.ts.net"
    const val CLOUD_ADDRESS = "https://cloud.paperclip.example.com"
    const val TEST_ADDRESS = "https://test.paperclip.example.com"
    const val USER_ID = "user-1"
    private const val NOW_EPOCH_SECONDS = 1_793_793_600L
    private const val HOME_KEY_DAYS = 23
    private const val CLOUD_KEY_EXPIRED_DAYS = 2
    private const val CREATED_DAYS_AGO = 60

    val acme = CompanyId(DemoIds.ACME)
    val sideProject = CompanyId(DemoIds.SIDE_PROJECT)

    fun companies() = listOf(CompanyRef(acme, "Acme Labs"), CompanyRef(sideProject, "Side Project Co"))

    fun access() =
        BoardAccess(
            profile = UserProfile(USER_ID, "Alex Operator", "alex@example.com", isInstanceAdmin = false),
            keyId = "key-demo-home",
            memberships = listOf(CompanyMembership(acme, Role.Owner), CompanyMembership(sideProject, Role.Owner)),
        )

    fun instances(): List<Instance> =
        listOf(
            instance(DemoIds.HOME_FACTORY, "Home factory", HOME_ADDRESS, Exposure.Private, 0)
                .copy(boardKeyId = "key-demo-home", keyExpiresAt = now + HOME_KEY_DAYS.days, defaultCompanyId = acme),
            instance(DemoIds.CLOUD_FACTORY, "Cloud factory", CLOUD_ADDRESS, Exposure.Public, 1)
                .copy(
                    boardKeyId = "key-demo-cloud",
                    keyExpiresAt = now - CLOUD_KEY_EXPIRED_DAYS.days,
                    status = InstanceStatus.SignedOut,
                ),
            instance(DemoIds.TEST_FACTORY, "Test factory", TEST_ADDRESS, Exposure.Private, 2)
                .copy(boardKeyId = null, status = InstanceStatus.SignedOut),
        )

    private fun instance(
        id: String,
        name: String,
        address: String,
        exposure: Exposure,
        order: Int,
    ) = Instance(
        id = InstanceId(id),
        displayName = name,
        baseUri = TestData.uri(address),
        deploymentMode = DeploymentMode.Authenticated,
        exposure = exposure,
        authMethod = AuthMethod.PairedBoardKey,
        boardKeyId = null,
        keyExpiresAt = null,
        defaultCompanyId = null,
        lastCompanyId = null,
        pins = emptyList(),
        createdAt = now - CREATED_DAYS_AGO.days,
        lastConnectedAt = now,
        sortOrder = order,
        status = InstanceStatus.SignedIn,
    )
}
