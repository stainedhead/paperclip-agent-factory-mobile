package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Review1b F-A-2: what a key that cannot be read turns into, before any request is sent. */
class StoredCredentialsTest {
    private val world = AuthWorld()
    private val id = InstanceId("inst-1")

    @Test
    fun `F-A-2 a declined key read is not Unauthorized and sends nothing`() =
        runTest {
            world.secrets.denyReads = true
            assertEquals(DomainResult.Failure(DomainError.DeviceAuthDeclined), world.gateway.me(id))
            assertEquals(0, world.server.requests.size)
        }

    @Test
    fun `F-A-2 an invalidated key is KeyInvalidated and sends nothing`() =
        runTest {
            world.secrets.invalidated = true
            assertEquals(DomainResult.Failure(DomainError.KeyInvalidated), world.gateway.me(id))
            assertEquals(0, world.server.requests.size)
        }

    @Test
    fun `F-A-2 a missing key is still Unauthorized`() =
        runTest {
            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                world.gateway.me(InstanceId("inst-signed-out")),
            )
        }
}
