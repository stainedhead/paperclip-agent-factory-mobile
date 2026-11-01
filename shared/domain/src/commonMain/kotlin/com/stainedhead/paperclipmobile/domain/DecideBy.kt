package com.stainedhead.paperclipmobile.domain

import kotlinx.datetime.LocalDate

/** When a person wants to decide an item, as the server stores it (research Q1, ADR-015). */
sealed interface DecideBy {
    data object Today : DecideBy

    data object ThisWeek : DecideBy

    data object Whenever : DecideBy

    data class OnDate(
        val date: LocalDate,
    ) : DecideBy

    /** Not set, or a value this app version does not understand. */
    data object None : DecideBy

    /** The string the triage endpoint accepts, or null for [None]. */
    fun toServer(): String? =
        when (this) {
            Today -> "today"
            ThisWeek -> "this_week"
            Whenever -> "whenever"
            is OnDate -> date.toString()
            None -> null
        }

    companion object {
        private val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")

        fun parse(raw: String?): DecideBy =
            when {
                raw == "today" -> Today
                raw == "this_week" -> ThisWeek
                raw == "whenever" -> Whenever
                raw != null && datePattern.matches(raw) -> parseDate(raw)
                else -> None
            }

        private fun parseDate(raw: String): DecideBy =
            try {
                OnDate(LocalDate.parse(raw))
            } catch (_: IllegalArgumentException) {
                None
            }
    }
}
