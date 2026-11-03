package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** A3: a live launch whose real graph could not be opened says so and runs nothing (never falls back to fakes). */
class StartupNoticeTest {
    @Test
    fun `a live launch whose graph did not open shows the notice`() {
        StartupNotice.forLaunch(demo = false, liveOpened = false) shouldBe StartupNotice.LiveGraphUnavailable
    }

    @Test
    fun `a live launch whose graph opened shows nothing`() {
        StartupNotice.forLaunch(demo = false, liveOpened = true) shouldBe null
    }

    @Test
    fun `a demo launch shows nothing whatever the live graph did`() {
        StartupNotice.forLaunch(demo = true, liveOpened = false) shouldBe null
        StartupNotice.forLaunch(demo = true, liveOpened = true) shouldBe null
    }
}
