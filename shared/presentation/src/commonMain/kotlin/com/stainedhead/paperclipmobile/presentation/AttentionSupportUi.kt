package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.SnoozePreset

data class MoneyUi(
    val minorUnits: Long,
    val currency: String,
)

internal fun Money.toUi() = MoneyUi(minorUnits, currency)

enum class SnoozePresetUi {
    OneHour,
    Tonight,
    Tomorrow,
    NextWeek,
}

internal fun SnoozePreset.toUi() = SnoozePresetUi.entries[ordinal]

internal fun SnoozePresetUi.toDomain() = SnoozePreset.entries[ordinal]

/** The web app address of a card: the factory address plus the card's relative path, or null without a path. */
internal fun webUrl(
    baseUrl: String,
    subject: AttentionSubject,
): String? = subject.href?.let { baseUrl.trimEnd('/') + "/" + it.trimStart('/') }

internal fun ResolverPolicy.toUi() = ResolverPolicyUi.entries[ordinal]
