package com.stainedhead.paperclipmobile.android.env

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LaunchConfigurationTest {
    @Test
    fun `no extras means the live graph`() {
        LaunchConfiguration.from(emptySet(), allowsDemoControls = true) shouldBe LaunchConfiguration()
    }

    @Test
    fun `the demo extra selects the demo graph in a debug build`() {
        LaunchConfiguration.from(setOf(LaunchConfiguration.EXTRA_DEMO), allowsDemoControls = true) shouldBe
            LaunchConfiguration(useDemo = true)
    }

    @Test
    fun `the first run demo extra selects the first run demo graph in a debug build`() {
        LaunchConfiguration.from(setOf(LaunchConfiguration.EXTRA_DEMO_FIRST_RUN), allowsDemoControls = true) shouldBe
            LaunchConfiguration(useFirstRunDemo = true)
    }

    @Test
    fun `a release build ignores every demo extra so a shipped app cannot be steered onto fakes`() {
        val extras = setOf(LaunchConfiguration.EXTRA_DEMO, LaunchConfiguration.EXTRA_DEMO_FIRST_RUN)

        LaunchConfiguration.from(extras, allowsDemoControls = false) shouldBe LaunchConfiguration()
    }
}
