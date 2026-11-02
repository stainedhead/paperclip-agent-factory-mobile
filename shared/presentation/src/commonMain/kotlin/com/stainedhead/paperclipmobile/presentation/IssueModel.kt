package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.issue.IssueView
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.PlanDocument
import com.stainedhead.paperclipmobile.domain.Role

/** Everything the Issue screen knows, before it is rendered into [IssueState]. */
internal data class IssueModel(
    val view: IssueView? = null,
    val interactions: List<Interaction> = emptyList(),
    val interactionsUnavailable: Boolean = false,
    val plan: PlanDocument? = null,
    val runs: List<HeartbeatRun>? = null,
    val files: List<Attachment>? = null,
    val tab: IssueTab = IssueTab.Thread,
    val tabError: ErrorUi? = null,
    val drafts: Map<String, InteractionDraft> = emptyMap(),
    val role: Role? = null,
    val userId: String? = null,
    val issueUrl: String? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    /** Counts finished actions. A read that started before one finished must not replace its result (F-B-7). */
    val version: Int = 0,
    val loadedAtMillis: Long? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)
