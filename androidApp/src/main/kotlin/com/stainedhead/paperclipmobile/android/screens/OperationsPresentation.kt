package com.stainedhead.paperclipmobile.android.screens

private const val SHORT_NAME = 3
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val TEN = 10L

/** Two or three letters for an avatar. The name is always written next to it, so this is decoration. */
object AgentInitials {
    /**
     * A short all-capitals name as it is ("CTO"), a name ending in a number as its first letter and the number
     * ("Engineer 2" gives "E2"), otherwise the first two letters ("QA agent" gives "QA").
     */
    fun of(name: String): String {
        val words = name.split(' ').filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return "?"
        val last = words.last()
        return when {
            first.length <= SHORT_NAME && first == first.uppercase() -> first
            words.size > 1 && last.all { it.isDigit() } -> first.take(1).uppercase() + last
            else -> first.take(2).uppercase()
        }
    }
}

/** Durations as the mocks write them. A negative one (a phone clock behind the server's) reads as zero. */
object RunTime {
    /** "45s", "4m 12s", "1h 02m": how long a run has been going, or went. */
    fun elapsed(seconds: Long): String {
        val total = maxOf(0L, seconds)
        return when {
            total < SECONDS_PER_MINUTE -> "${total}s"
            total < SECONDS_PER_HOUR -> "${total / SECONDS_PER_MINUTE}m ${pad(total % SECONDS_PER_MINUTE)}s"
            else -> "${total / SECONDS_PER_HOUR}h ${pad(total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE)}m"
        }
    }

    /** "0:02", "2:55", "1:02:03": where in the run an event happened. */
    fun offset(seconds: Long): String {
        val total = maxOf(0L, seconds)
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val rest = total % SECONDS_PER_MINUTE
        return if (hours > 0) "$hours:${pad(minutes)}:${pad(rest)}" else "$minutes:${pad(rest)}"
    }

    private fun pad(value: Long) = if (value < TEN) "0$value" else value.toString()
}
