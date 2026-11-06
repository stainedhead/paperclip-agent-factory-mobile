package com.stainedhead.paperclipmobile.umbrella

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** AND-004: what the Android app chooses when it opens the live graph, mirroring `IosLiveSettings`. */
class AndroidLiveSettingsTest {
    @Test
    fun `the settings carry the prompt texts the app localizes`() {
        val settings =
            AndroidLiveSettings(
                unlockReason = "Unlock",
                confirmReason = "Confirm",
                writeReason = "Protect",
                keyReadReason = "Read",
            )

        settings.unlockReason shouldBe "Unlock"
        settings.confirmReason shouldBe "Confirm"
        settings.writeReason shouldBe "Protect"
        settings.keyReadReason shouldBe "Read"
    }

    @Test
    fun `the key alias prefix defaults to the one the Keystore store has always used`() {
        AndroidLiveSettings("a", "b", "c", "d").keyAliasPrefix shouldBe "pafm.secret/"
    }

    @Test
    fun `a blank key alias prefix is refused so keys of other uses cannot be touched`() {
        shouldThrow<IllegalArgumentException> { AndroidLiveSettings("a", "b", "c", "d", keyAliasPrefix = " ") }
    }
}
