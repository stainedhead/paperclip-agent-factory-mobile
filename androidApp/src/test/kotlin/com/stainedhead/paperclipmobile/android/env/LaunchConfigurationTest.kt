package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.domain.port.BiometricResult
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

        LaunchConfiguration.from(extras, allowsDemoControls = false, biometrics = "cancelled") shouldBe
            LaunchConfiguration()
    }

    @Test
    fun `the prompt answers are scripted in order and unknown words are ignored`() {
        LaunchConfiguration
            .from(
                setOf(LaunchConfiguration.EXTRA_DEMO),
                allowsDemoControls = true,
                biometrics = "Cancelled,bogus,approved",
            ).scriptedBiometrics shouldBe listOf(BiometricResult.Cancelled, BiometricResult.Approved)
    }

    @Test
    fun `the generated Inbox card count applies to the demo graph only in a build that allows demo controls`() {
        val demo = setOf(LaunchConfiguration.EXTRA_DEMO)

        LaunchConfiguration.from(demo, allowsDemoControls = true, inboxCards = 200).inboxCards shouldBe 200
        LaunchConfiguration.from(demo, allowsDemoControls = false, inboxCards = 200).inboxCards shouldBe 0
        LaunchConfiguration.from(demo, allowsDemoControls = true, inboxCards = -5).inboxCards shouldBe 0
    }
}
