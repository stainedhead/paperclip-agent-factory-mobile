package com.stainedhead.paperclipmobile.infrastructure.securestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** Proves the emulator harness runs for this module; the Keystore tests (T1.2) build on it. */
@RunWith(AndroidJUnit4::class)
class DeviceHarnessSmokeTest {
    @Test
    fun theInstrumentedTestRunsOnADeviceWithAFileArea() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assertTrue(context.noBackupFilesDir.exists() || context.noBackupFilesDir.mkdirs())
    }
}
