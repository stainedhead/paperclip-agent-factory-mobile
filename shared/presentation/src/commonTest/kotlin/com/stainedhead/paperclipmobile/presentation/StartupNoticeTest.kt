package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** A3: a launch on the real factories while the real adapters are not in this build says so, and runs nothing. */
class StartupNoticeTest {
    @Test
    fun `a live launch without live adapters shows the unavailable notice`() {
        StartupNotice.forLaunch(demo = false, liveAvailable = false) shouldBe StartupNotice.LiveFactoriesUnavailable
    }

    @Test
    fun `a live launch with live adapters shows nothing`() {
        StartupNotice.forLaunch(demo = false, liveAvailable = true) shouldBe null
    }

    @Test
    fun `a demo launch shows nothing whatever the live adapters are`() {
        StartupNotice.forLaunch(demo = true, liveAvailable = false) shouldBe null
        StartupNotice.forLaunch(demo = true, liveAvailable = true) shouldBe null
    }
}
