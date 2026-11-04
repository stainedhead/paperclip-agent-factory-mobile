package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** R-019: which words, tone and Pair again a factory's status earns is decided in shared code. */
class FactoryStatusRulesTest {
    private fun expiry(state: KeyExpiryUi) = ExpiryUi(state, if (state == KeyExpiryUi.NoExpiry) null else 5)

    @Test
    fun `R-019 an expired key outranks the status and offers pairing`() {
        FactoryStatusUi.entries.forEach { status ->
            val decision = FactoryStatusDecision.of(status, expiry(KeyExpiryUi.Expired))
            decision.label shouldBe FactoryStatusLabelUi.KeyExpired
            decision.tone shouldBe ToneUi.Warn
            decision.offersPairing shouldBe true
        }
    }

    @Test
    fun `R-019 a signed in factory with a good key is connected and offers no pairing`() {
        for (state in listOf(KeyExpiryUi.NoExpiry, KeyExpiryUi.Valid, KeyExpiryUi.ExpiringSoon)) {
            val decision = FactoryStatusDecision.of(FactoryStatusUi.SignedIn, expiry(state))
            decision.label shouldBe FactoryStatusLabelUi.Connected
            decision.tone shouldBe ToneUi.Success
            decision.offersPairing shouldBe false
        }
    }

    @Test
    fun `R-019 signed out and needs re pairing both offer pairing`() {
        FactoryStatusDecision.of(FactoryStatusUi.SignedOut, expiry(KeyExpiryUi.Valid)) shouldBe
            FactoryStatusDecision(FactoryStatusLabelUi.SignedOut, ToneUi.Neutral, offersPairing = true)
        FactoryStatusDecision.of(FactoryStatusUi.NeedsRePairing, expiry(KeyExpiryUi.Valid)) shouldBe
            FactoryStatusDecision(FactoryStatusLabelUi.NeedsRePairing, ToneUi.Warn, offersPairing = true)
    }

    @Test
    fun `R-019 the expiry decision carries the days and a tone`() {
        ExpiryDecision.of(ExpiryUi(KeyExpiryUi.NoExpiry, null)) shouldBe ExpiryDecision(ExpiryKindUi.None, null, ToneUi.Neutral)
        ExpiryDecision.of(ExpiryUi(KeyExpiryUi.Expired, 0)) shouldBe ExpiryDecision(ExpiryKindUi.Expired, null, ToneUi.Warn)
        ExpiryDecision.of(ExpiryUi(KeyExpiryUi.Valid, 23)) shouldBe ExpiryDecision(ExpiryKindUi.Days, 23, ToneUi.Neutral)
        ExpiryDecision.of(ExpiryUi(KeyExpiryUi.ExpiringSoon, 3)) shouldBe ExpiryDecision(ExpiryKindUi.Days, 3, ToneUi.Warn)
    }
}
