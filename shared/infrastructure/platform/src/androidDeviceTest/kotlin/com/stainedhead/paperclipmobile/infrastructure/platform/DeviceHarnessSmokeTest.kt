package com.stainedhead.paperclipmobile.infrastructure.platform

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

/** Proves the emulator harness runs for this module; the connectivity tests (T1.4) build on it. */
@RunWith(AndroidJUnit4::class)
class DeviceHarnessSmokeTest {
    @Test
    fun theInstrumentedTestRunsOnADeviceWithTheConnectivityService() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assertNotNull(context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
    }
}
