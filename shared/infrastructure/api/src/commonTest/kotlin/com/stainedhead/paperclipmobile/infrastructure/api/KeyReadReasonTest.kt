package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * F-3 (ADR-151): the text the secret store gets as the reason for reading the board key is the one the platform
 * supplied, for every adapter that reads it, and never an internal literal such as "call paperclip".
 */
class KeyReadReasonTest {
    private val reason = "Read the board key to talk to the factory (test text)"

    @Test
    fun `an auth call reads the key with the injected reason`() =
        runTest {
            val world = AuthWorld()
            val gateway = AuthGatewayAdapter(world.client, world.instances, world.secrets, reason)

            gateway.me(InstanceId("inst-1"))

            world.secrets.readReasons shouldContainOnly listOf(reason)
        }

    @Test
    fun `a company call reads the key with the injected reason`() =
        runTest {
            val world = AuthWorld()
            val gateway = DashboardGatewayAdapter(world.client, world.instances, world.secrets, reason)

            gateway.dashboard(CompanyScope(InstanceId("inst-1"), TestData.acme))

            world.secrets.readReasons shouldContainOnly listOf(reason)
        }

    @Test
    fun `no adapter reads the key with the old internal literal`() =
        runTest {
            val world = AuthWorld()

            AuthGatewayAdapter(world.client, world.instances, world.secrets, reason).me(InstanceId("inst-1"))

            world.secrets.readReasons.contains("call paperclip") shouldBe false
        }
}
