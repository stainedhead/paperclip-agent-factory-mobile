package com.stainedhead.paperclipmobile.domain

/** How urgent an Attention item is. Orders cards inside a group; never moves a card between groups (ADR-015). */
enum class Severity {
    Critical,
    High,
    Medium,
    Low,
    ;

    /** Lower sorts first. */
    val rank: Int get() = ordinal

    companion object {
        /** Unknown or missing values from a newer server read as [Medium] so nothing crashes or hides. */
        fun fromServer(raw: String?): Severity =
            when (raw) {
                "critical" -> Critical
                "high" -> High
                "low" -> Low
                else -> Medium
            }
    }
}
