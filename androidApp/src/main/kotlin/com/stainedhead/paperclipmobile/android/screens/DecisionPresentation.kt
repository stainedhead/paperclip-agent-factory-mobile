package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.presentation.EffectKindUi
import com.stainedhead.paperclipmobile.presentation.EffectSummaryUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.TimeZone

/*
 * The small lookups of the Decision screen (AND-040). Which options exist, what is enabled, when the device owner is
 * asked and what the final state is stay in the shared holder; this only picks the words and shapes the figures.
 */

/** "in_progress" reads "in progress": the server's words with the underscores taken out. */
fun prettify(raw: String): String = raw.replace('_', ' ')

/**
 * What goes into an effect's sentence. Only a status is a server word ("in_progress"); a title, a comment excerpt or a
 * name is shown as written, so the line says exactly what the option will do.
 */
fun effectDetail(effect: EffectSummaryUi): String {
    val detail = effect.detail.orEmpty()
    return if (effect.kind == EffectKindUi.UpdateIssueStatus) prettify(detail) else detail
}

/** "update_issue_status" reads "Update issue status". */
fun resultTitle(effectType: String): String = prettify(effectType).replaceFirstChar { it.uppercase() }

/** Why an effect did not run: a changed target has its own sentence, any other reason is the server's word. */
sealed interface ResultReason {
    data object None : ResultReason

    data object TargetChanged : ResultReason

    data class Other(
        val text: String,
    ) : ResultReason

    companion object {
        fun of(reason: String?): ResultReason =
            when {
                reason.isNullOrEmpty() -> None
                reason == "target_changed" -> TargetChanged
                else -> Other(prettify(reason))
            }
    }
}

/** When the decision lapses by itself, as a date and a time in the phone's zone. */
fun expiryDateTime(
    epochMillis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(timeZone.toZoneId())
        .format(Instant.ofEpochMilli(epochMillis))

/** A `YYYY-MM-DD` decide-by as a date (a calendar date, not a moment: no zone shifts it); other text as it is. */
fun decideByDate(
    iso: String,
    locale: Locale = Locale.getDefault(),
): String =
    runCatching {
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.of("UTC"))
            .format(LocalDate.parse(iso))
    }.getOrDefault(iso)

/** "3 / 40": how much was typed and the limit. */
data class CounterText(
    val typed: String,
    val limit: String,
)

/** The counter under an input that has a limit, none for one that has no limit. */
fun inputCounter(
    typed: String,
    maxLength: Int?,
): CounterText? = maxLength?.let { CounterText(typed.length.toString(), it.toString()) }
