package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.testing.contract.ClockContract
import com.stainedhead.paperclipmobile.testing.contract.DeviceTimeZoneContract
import com.stainedhead.paperclipmobile.testing.contract.IdGeneratorContract
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

class SystemClockContractTest : ClockContract() {
    override fun create(): Clock = SystemClock()

    override suspend fun advance(clock: Clock) {
        // Real time must pass; the test dispatcher's virtual delay would not move the system clock.
        withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
    }

    @Test
    fun `AD-032 the system clock obeys the clock contract`() = runTest { runAll() }

    private companion object {
        const val WAIT_MILLIS = 20L
    }
}

class UuidIdGeneratorContractTest : IdGeneratorContract() {
    override fun create(): IdGenerator = UuidIdGenerator()

    @Test
    fun `AD-032 the uuid generator obeys the id generator contract`() = runTest { runAll() }

    @Test
    fun `AD-032 an id is a lower case canonical uuid`() {
        val id = UuidIdGenerator().generate()
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(id) shouldBe true
    }
}

class SystemDeviceTimeZoneContractTest : DeviceTimeZoneContract() {
    override fun create(): DeviceTimeZone = SystemDeviceTimeZone()

    @Test
    fun `AD-032 the system zone obeys the device time zone contract`() = runTest { runAll() }
}

class FailClosedPlatformServicesTest {
    @Test
    fun `AD-033 biometrics without a platform bridge report unavailable for every reason and never approve`() =
        runTest {
            val biometrics = UnavailableBiometrics()
            AuthReason.entries.forEach { biometrics.authenticate(it) shouldBe BiometricResult.Unavailable }
        }

    @Test
    fun `AD-032 connectivity without a platform monitor is optimistic and stays online`() {
        OptimisticConnectivity().isOnline.value shouldBe true
    }
}
