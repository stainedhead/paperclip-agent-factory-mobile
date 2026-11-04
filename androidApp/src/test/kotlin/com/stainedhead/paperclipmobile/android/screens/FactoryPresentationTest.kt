package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.presentation.ExpiryUi
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.KeyExpiryUi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The lookups behind the status words and colours of a factory (the iPhone app's `FactoryPresentation`). */
class FactoryPresentationTest {
    private val noExpiry = ExpiryUi(KeyExpiryUi.NoExpiry, null)
    private val expired = ExpiryUi(KeyExpiryUi.Expired, null)

    @Test
    fun `a connected factory with a working key is connected and green and needs no pairing`() {
        val presentation = FactoryStatusPresentation.of(FactoryStatusUi.SignedIn, ExpiryUi(KeyExpiryUi.Valid, 23))

        presentation shouldBe FactoryStatusPresentation(FactoryStatusLabel.Connected, StatusTone.Success, false)
    }

    @Test
    fun `an expired key reads as key expired and offers pairing, whatever the status is`() {
        FactoryStatusPresentation.of(FactoryStatusUi.SignedIn, expired) shouldBe
            FactoryStatusPresentation(FactoryStatusLabel.KeyExpired, StatusTone.Warn, true)
        FactoryStatusPresentation.of(FactoryStatusUi.SignedOut, expired) shouldBe
            FactoryStatusPresentation(FactoryStatusLabel.KeyExpired, StatusTone.Warn, true)
    }

    @Test
    fun `a signed out factory is neutral and offers pairing`() {
        FactoryStatusPresentation.of(FactoryStatusUi.SignedOut, noExpiry) shouldBe
            FactoryStatusPresentation(FactoryStatusLabel.SignedOut, StatusTone.Neutral, true)
    }

    @Test
    fun `a factory that needs pairing again is a warning and offers pairing`() {
        FactoryStatusPresentation.of(FactoryStatusUi.NeedsRePairing, ExpiryUi(KeyExpiryUi.Valid, 5)) shouldBe
            FactoryStatusPresentation(FactoryStatusLabel.NeedsRePairing, StatusTone.Warn, true)
    }

    @Test
    fun `a key that never expires says nothing, an expired one says so in a warning`() {
        ExpiryPresentation.of(noExpiry) shouldBe ExpiryPresentation.None
        ExpiryPresentation.of(expired) shouldBe ExpiryPresentation.Expired
        ExpiryPresentation.Expired.tone shouldBe StatusTone.Warn
    }

    @Test
    fun `days left are neutral until the key is expiring soon`() {
        ExpiryPresentation.of(ExpiryUi(KeyExpiryUi.Valid, 23)) shouldBe ExpiryPresentation.Days(23, StatusTone.Neutral)
        ExpiryPresentation.of(ExpiryUi(KeyExpiryUi.ExpiringSoon, 3)) shouldBe
            ExpiryPresentation.Days(3, StatusTone.Warn)
        ExpiryPresentation.of(ExpiryUi(KeyExpiryUi.Valid, null)) shouldBe ExpiryPresentation.Days(0, StatusTone.Neutral)
    }
}
