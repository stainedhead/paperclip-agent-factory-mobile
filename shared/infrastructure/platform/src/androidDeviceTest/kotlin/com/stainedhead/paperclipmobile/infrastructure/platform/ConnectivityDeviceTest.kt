package com.stainedhead.paperclipmobile.infrastructure.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real `ConnectivityManager` on an emulator, including a switch of the whole network with airplane mode. */
@RunWith(AndroidJUnit4::class)
class ConnectivityDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.context
    private val opened = mutableListOf<ConnectivityManagerConnectivity>()

    @After
    fun restoreTheNetwork() {
        shell("cmd connectivity airplane-mode disable")
        opened.forEach { it.close() }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    private fun open() = ConnectivityManagerConnectivity(context).also { opened += it }

    private fun waitFor(
        expected: Boolean,
        connectivity: ConnectivityManagerConnectivity,
    ): Boolean {
        repeat(WAIT_STEPS) {
            if (connectivity.isOnline.value == expected) return true
            Thread.sleep(STEP_MILLIS)
        }
        return connectivity.isOnline.value == expected
    }

    @Test
    fun theFirstValueIsTheValidatedStateOfTheDefaultNetwork() {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        val validated =
            capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        assertEquals(validated, open().isOnline.value)
    }

    @Test
    fun theValueFollowsAirplaneModeOffAndOnAndCloseUnregistersCleanly() {
        val connectivity = open()
        assertTrue(waitFor(true, connectivity), "the emulator starts online")
        shell("cmd connectivity airplane-mode enable")
        assertTrue(waitFor(false, connectivity), "airplane mode must report offline")
        shell("cmd connectivity airplane-mode disable")
        assertTrue(waitFor(true, connectivity), "leaving airplane mode must report online again")
        connectivity.close()
        connectivity.close()
        shell("cmd connectivity airplane-mode enable")
        Thread.sleep(SETTLE_MILLIS)
        assertTrue(connectivity.isOnline.value, "a closed monitor no longer follows the network")
    }

    private companion object {
        const val WAIT_STEPS = 60
        const val STEP_MILLIS = 250L
        const val SETTLE_MILLIS = 1500L
    }
}
