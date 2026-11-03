package com.stainedhead.paperclipmobile.domain.port

import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The page asked of `GET /api/companies/{c}/attention` (research, feed request). The app always sorts by decide-by. */
data class FeedQuery(
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/** The Attention feed and the per-person dismissal and triage endpoints. */
interface AttentionGateway {
    suspend fun feed(
        scope: CompanyScope,
        query: FeedQuery,
    ): DomainResult<AttentionFeed>

    /** `POST .../inbox-dismissals` with `kind: dismiss`. [itemKey] is the item's `dismissalKey`. */
    suspend fun dismiss(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Dismissal>

    /** `POST .../inbox-dismissals` with `kind: snooze`; the server refuses a time that is not in the future. */
    suspend fun snooze(
        scope: CompanyScope,
        itemKey: String,
        until: Instant,
    ): DomainResult<Dismissal>

    /** `DELETE .../inbox-dismissals/{itemKey}`: undoes a dismissal or a snooze. Idempotent. */
    suspend fun restore(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Unit>

    /** `PUT .../decision-triage/{sourceKind}/{sourceId}`; [decideBy] [DecideBy.None] clears it. */
    suspend fun setDecideBy(
        scope: CompanyScope,
        sourceKind: AttentionSourceKind,
        sourceId: String,
        decideBy: DecideBy,
    ): DomainResult<Unit>
}

/** The first page of the feed per factory and company, so the Inbox renders before the network answers (ADR-017). */
interface AttentionCache {
    suspend fun read(scope: CompanyScope): AttentionFeed?

    suspend fun write(
        scope: CompanyScope,
        feed: AttentionFeed,
    )

    suspend fun clear(scope: CompanyScope)

    companion object {
        /**
         * The most items a cache keeps per scope. A longer page is cut to this many and its `nextCursor` is cleared,
         * because the server cursor points past the dropped items and paging on would skip them.
         */
        const val MAX_ITEMS = 200
    }
}

/** `GET /api/decisions/{id}`, decide and dismiss (research Q4, Q8). */
interface DecisionGateway {
    suspend fun get(
        scope: CompanyScope,
        id: DecisionId,
    ): DomainResult<Decision>

    /** The response already carries the outcome; [idempotencyKey] makes a manual re-tap replay (ADR-018). */
    suspend fun decide(
        scope: CompanyScope,
        id: DecisionId,
        optionId: String,
        inputValues: Map<String, String>,
        idempotencyKey: String,
    ): DomainResult<Decision>

    suspend fun dismiss(
        scope: CompanyScope,
        id: DecisionId,
        reason: String?,
    ): DomainResult<Decision>
}

/** The Approvals endpoints (PRD FR-050, FR-051). A missing [note] is sent as no note. */
interface ApprovalGateway {
    suspend fun get(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<Approval>

    suspend fun linkedIssues(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<LinkedIssue>>

    suspend fun comments(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<ApprovalComment>>

    suspend fun approve(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval>

    suspend fun reject(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval>

    suspend fun requestRevision(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval>

    suspend fun addComment(
        scope: CompanyScope,
        id: ApprovalId,
        body: String,
    ): DomainResult<ApprovalComment>
}

/** Budget incidents. There is no single-incident endpoint, so [incident] reads the overview (research Q5). */
interface BudgetGateway {
    /** `NotFound` when the incident is no longer active (resolved or dismissed). */
    suspend fun incident(
        scope: CompanyScope,
        id: BudgetIncidentId,
    ): DomainResult<BudgetIncident>

    suspend fun resolve(
        scope: CompanyScope,
        id: BudgetIncidentId,
        resolution: BudgetResolution,
        note: String?,
    ): DomainResult<BudgetIncident>
}

/** The person's current time zone, for snooze presets (ADR-020). */
interface DeviceTimeZone {
    fun current(): TimeZone
}
