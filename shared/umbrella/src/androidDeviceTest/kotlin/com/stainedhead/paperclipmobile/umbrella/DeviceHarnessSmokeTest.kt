package com.stainedhead.paperclipmobile.umbrella

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** Proves the emulator harness runs for this module; the live-graph smoke test (T8.9) builds on it. */
@RunWith(AndroidJUnit4::class)
class DeviceHarnessSmokeTest {
    @Test
    fun theInstrumentedTestRunsOnADevice() {
        assertTrue(
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.packageName
                .isNotEmpty(),
        )
    }
}
