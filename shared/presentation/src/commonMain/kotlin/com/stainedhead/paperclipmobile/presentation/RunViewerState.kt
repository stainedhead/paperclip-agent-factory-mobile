package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.run.CancelRun
import com.stainedhead.paperclipmobile.application.run.LoadRun
import com.stainedhead.paperclipmobile.application.run.TailRunLog
import com.stainedhead.paperclipmobile.domain.RunEvent

enum class RunStreamUi {
    System,
    Stdout,
    Stderr,
}

enum class RunLevelUi {
    Info,
    Warn,
    Error,
}

data class RunEventUi(
    val seq: Long,
    val type: String,
    val stream: RunStreamUi?,
    val level: RunLevelUi?,
    val message: String?,
    val createdAtEpochMillis: Long,
)

internal fun RunEvent.toUi() =
    RunEventUi(
        seq,
        type,
        stream?.let { RunStreamUi.valueOf(it.name) },
        level?.let { RunLevelUi.valueOf(it.name) },
        message,
        createdAt.toEpochMilliseconds(),
    )

data class RunViewerState(
    val phase: DetailPhase = DetailPhase.Loading,
    val run: RunRowUi? = null,
    val events: List<RunEventUi> = emptyList(),
    val logText: String = "",
    /** The start of the log was dropped to bound memory. */
    val logDroppedStart: Boolean = false,
    val logUnavailable: Boolean = false,
    /** True while the screen follows the end of the log; the person scrolling up turns it off. */
    val autoScroll: Boolean = true,
    val isLive: Boolean = false,
    val canCancel: Boolean = false,
    val confirmCancel: Boolean = false,
    val isCancelling: Boolean = false,
    val role: RoleUi? = null,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val updatedAtEpochMillis: Long? = null,
    /** 5 seconds while the run is live, 0 when it is finished and needs no refresh (PRD FR-090). */
    val refreshIntervalSeconds: Int = 0,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

sealed interface RunViewerIntent {
    data object Refresh : RunViewerIntent

    data class UserScrolled(
        val atBottom: Boolean,
    ) : RunViewerIntent

    data object JumpToLatest : RunViewerIntent

    data object CancelTapped : RunViewerIntent

    data object CancelConfirmed : RunViewerIntent

    data object CancelDismissed : RunViewerIntent

    data object MessageDismissed : RunViewerIntent
}

data class RunViewerUseCases(
    val resolveContext: ResolveCompanyContext,
    val load: LoadRun,
    val tail: TailRunLog,
    val cancel: CancelRun,
)
