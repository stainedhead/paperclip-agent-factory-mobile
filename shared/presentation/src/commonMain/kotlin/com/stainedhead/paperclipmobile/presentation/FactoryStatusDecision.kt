package com.stainedhead.paperclipmobile.presentation

/** The words a factory's status can have. An expired key outranks the status: it is what the person must fix. */
enum class FactoryStatusLabelUi {
    Connected,
    KeyExpired,
    SignedOut,
    NeedsRePairing,
}

/**
 * Which words and tone a factory's status gets, from the two facts the shared state gives (status and key expiry).
 * "Pair again" is offered whenever this phone has no working key for the factory (R-019: decided here, for both apps).
 */
data class FactoryStatusDecision(
    val label: FactoryStatusLabelUi,
    val tone: ToneUi,
    val offersPairing: Boolean,
) {
    companion object {
        fun of(
            status: FactoryStatusUi,
            expiry: ExpiryUi,
        ): FactoryStatusDecision {
            val expired = expiry.state == KeyExpiryUi.Expired
            val (label, tone) =
                when {
                    expired -> FactoryStatusLabelUi.KeyExpired to ToneUi.Warn
                    status == FactoryStatusUi.SignedIn -> FactoryStatusLabelUi.Connected to ToneUi.Success
                    status == FactoryStatusUi.SignedOut -> FactoryStatusLabelUi.SignedOut to ToneUi.Neutral
                    else -> FactoryStatusLabelUi.NeedsRePairing to ToneUi.Warn
                }
            return FactoryStatusDecision(label, tone, offersPairing = status != FactoryStatusUi.SignedIn || expired)
        }
    }
}

/** What an expiry says. */
enum class ExpiryKindUi {
    /** The key never expires: nothing is shown. */
    None,
    Expired,

    /** Valid or expiring soon: "expires in [ExpiryDecision.days] days". */
    Days,
}

/** "Board key expires in 23 days": the kind, the days for [ExpiryKindUi.Days] and the tone of a key's expiry. */
data class ExpiryDecision(
    val kind: ExpiryKindUi,
    val days: Int?,
    val tone: ToneUi,
) {
    companion object {
        fun of(expiry: ExpiryUi): ExpiryDecision =
            when (expiry.state) {
                KeyExpiryUi.NoExpiry -> ExpiryDecision(ExpiryKindUi.None, null, ToneUi.Neutral)
                KeyExpiryUi.Expired -> ExpiryDecision(ExpiryKindUi.Expired, null, ToneUi.Warn)
                KeyExpiryUi.Valid -> ExpiryDecision(ExpiryKindUi.Days, expiry.daysLeft ?: 0, ToneUi.Neutral)
                KeyExpiryUi.ExpiringSoon -> ExpiryDecision(ExpiryKindUi.Days, expiry.daysLeft ?: 0, ToneUi.Warn)
            }
    }
}
