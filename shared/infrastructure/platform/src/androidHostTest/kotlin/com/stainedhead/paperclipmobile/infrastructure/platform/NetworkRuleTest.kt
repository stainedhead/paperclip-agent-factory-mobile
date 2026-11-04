package com.stainedhead.paperclipmobile.infrastructure.platform

import android.net.NetworkCapabilities
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * R-012 / DEC-2: the app is online whenever the default network declares internet access, validated or not. A home
 * LAN, split DNS, a captive portal or a VPN whose probe fails is "online, unknown": the request is attempted and the
 * real success or typed failure shows (INTENT principle 7: home factories behind a VPN are the primary case).
 */
class NetworkRuleTest {
    private fun networkWith(vararg capabilities: Int): (Int) -> Boolean = { it in capabilities }

    @Test
    fun `R-012 a validated network with internet is online`() {
        isUsable(
            networkWith(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        ) shouldBe true
    }

    @Test
    fun `R-012 a network with internet that the system could not validate is still online`() {
        isUsable(networkWith(NetworkCapabilities.NET_CAPABILITY_INTERNET)) shouldBe true
    }

    @Test
    fun `R-012 a network without internet is offline even when validated`() {
        isUsable(networkWith(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) shouldBe false
        isUsable(networkWith()) shouldBe false
    }
}
