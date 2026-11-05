package com.stainedhead.paperclipmobile.infrastructure.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** Proves the emulator harness runs for this module; the `openAndroidPersistence` tests (T1.5) build on it. */
@RunWith(AndroidJUnit4::class)
class DeviceHarnessSmokeTest {
    @Test
    fun theInstrumentedTestRunsOnADeviceWithADatabaseArea() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assertTrue(context.getDatabasePath("smoke.db").parentFile != null)
    }
}
