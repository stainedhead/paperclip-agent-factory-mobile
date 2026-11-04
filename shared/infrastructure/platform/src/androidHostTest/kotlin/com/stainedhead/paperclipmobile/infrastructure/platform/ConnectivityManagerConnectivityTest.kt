package com.stainedhead.paperclipmobile.infrastructure.platform

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** A network monitor the test drives by hand, standing in for `ConnectivityManager`. */
internal class FakeNetworkMonitor(
    var online: Boolean = true,
    private val registrationFails: Boolean = false,
) : NetworkMonitor {
    var registrations = 0
    var unregistrations = 0
    private var listener: ((Boolean) -> Unit)? = null

    override fun currentlyOnline(): Boolean = online

    override fun register(onChange: (Boolean) -> Unit): AutoCloseable {
        check(!registrationFails) { "the system refused the callback" }
        registrations++
        listener = onChange
        return AutoCloseable { unregistrations++ }
    }

    /** The system reports a change. It is delivered even after unregistering, like a callback already in flight. */
    fun report(usable: Boolean) {
        listener?.invoke(usable)
    }
}

class ConnectivityManagerConnectivityTest {
    @Test
    fun `AND-003 the first value is read from the current default network before any callback`() {
        ConnectivityManagerConnectivity(FakeNetworkMonitor(online = false)).isOnline.value shouldBe false
        ConnectivityManagerConnectivity(FakeNetworkMonitor(online = true)).isOnline.value shouldBe true
    }

    @Test
    fun `AND-003 the value follows the validated state of the default network`() {
        val monitor = FakeNetworkMonitor(online = true)
        val connectivity = ConnectivityManagerConnectivity(monitor)
        monitor.report(usable = false)
        connectivity.isOnline.value shouldBe false
        monitor.report(usable = true)
        connectivity.isOnline.value shouldBe true
        monitor.report(usable = false)
        monitor.report(usable = false)
        connectivity.isOnline.value shouldBe false
    }

    @Test
    fun `AND-003 a callback the system refuses is offline, not a crash`() {
        val connectivity = ConnectivityManagerConnectivity(FakeNetworkMonitor(online = true, registrationFails = true))
        connectivity.isOnline.value shouldBe false
        connectivity.close()
    }

    @Test
    fun `AND-003 close unregisters once and later changes are ignored`() {
        val monitor = FakeNetworkMonitor(online = true)
        val connectivity = ConnectivityManagerConnectivity(monitor)
        monitor.registrations shouldBe 1
        connectivity.close()
        connectivity.close()
        monitor.unregistrations shouldBe 1
        monitor.report(usable = false)
        connectivity.isOnline.value shouldBe true
    }
}
