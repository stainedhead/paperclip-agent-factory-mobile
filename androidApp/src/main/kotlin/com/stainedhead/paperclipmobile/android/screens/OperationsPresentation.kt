package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.RunStatusUi

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

/** A run that has not ended: it can still change, so it is timed to the second and refreshed (PRD FR-090). */
fun RunStatusUi.isLive(): Boolean =
    this == RunStatusUi.Running || this == RunStatusUi.Queued || this == RunStatusUi.ScheduledRetry

fun RunStatusUi.tone(): StatusTone =
    when (this) {
        RunStatusUi.Running, RunStatusUi.Succeeded -> StatusTone.Success
        RunStatusUi.Failed, RunStatusUi.TimedOut -> StatusTone.Critical
        RunStatusUi.Cancelled, RunStatusUi.Interrupted -> StatusTone.Warn
        RunStatusUi.Queued, RunStatusUi.ScheduledRetry, RunStatusUi.Unknown -> StatusTone.Neutral
    }

fun AgentStatusUi.tone(): StatusTone =
    when (this) {
        AgentStatusUi.Running -> StatusTone.Success
        AgentStatusUi.Error -> StatusTone.Critical
        AgentStatusUi.Paused, AgentStatusUi.PendingApproval -> StatusTone.Warn
        AgentStatusUi.Active, AgentStatusUi.Idle, AgentStatusUi.Terminated, AgentStatusUi.Unknown -> StatusTone.Neutral
    }

/**
 * The buttons of an agent row. While the agent has a run in progress the row offers View run where Run now would be
 * (the mock's CTO row), so no second run is started by a tap meant for the first; the rest are the holder's.
 */
fun AgentRowUi.visibleActions(): List<AgentActionUi> {
    val running = currentRun?.status?.isLive() == true
    return actions.filter { !(it == AgentActionUi.RunNow && running) }
}
