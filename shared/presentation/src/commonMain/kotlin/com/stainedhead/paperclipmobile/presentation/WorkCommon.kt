package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.SpendWindow
import com.stainedhead.paperclipmobile.domain.SpendWindowKind
import kotlinx.coroutines.flow.MutableStateFlow

/** The refresh interval a visible screen needs (PRD FR-090); the polling policy itself is slice 1a-4. */
internal const val SCREEN_REFRESH_SECONDS = 15
internal const val RUNNING_REFRESH_SECONDS = 5

internal fun CompanyTarget.toScope() = CompanyScope(InstanceId(instanceId), CompanyId(companyId))

/** Resolves who the person is in one company once and remembers it for the life of a screen (ADR-011). */
internal class ContextSource(
    private val resolve: ResolveCompanyContext,
    val scope: CompanyScope,
) {
    private val cached = MutableStateFlow<CompanyContext?>(null)

    val role get() = cached.value?.role

    val userId get() = cached.value?.userId

    /** Resolves the role again after a 403; keeps the old one if that read fails. Returns the new context or null. */
    suspend fun reResolve(): CompanyContext? =
        (resolve(scope) as? DomainResult.Success)?.value?.also { cached.value = it }

    suspend fun get(): DomainResult<CompanyContext> =
        cached.value?.let { DomainResult.Success(it) }
            ?: resolve(scope).also { if (it is DomainResult.Success) cached.value = it.value }
}

enum class SpendWindowUi {
    Last5Hours,
    Last24Hours,
    Last7Days,
}

data class SpendUi(
    val window: SpendWindowUi,
    val cost: MoneyUi,
)

internal fun SpendWindow.toUi() =
    SpendUi(
        when (window) {
            SpendWindowKind.Last5Hours -> SpendWindowUi.Last5Hours
            SpendWindowKind.Last24Hours -> SpendWindowUi.Last24Hours
            SpendWindowKind.Last7Days -> SpendWindowUi.Last7Days
        },
        cost.toUi(),
    )

/** A run in a list: Factory home, an agent's recent runs, an issue's Runs tab. */
data class RunRowUi(
    val runId: String,
    val agentName: String?,
    val issueId: String?,
    val status: RunStatusUi,
    val startedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    /** Null while the run is in progress. */
    val finishedAtEpochMillis: Long?,
    /** The issue's identifier and title ("PAP-1204"), when they could be read. */
    val issueIdentifier: String?,
    val issueTitle: String?,
)

internal fun HeartbeatRun.toRow(issue: com.stainedhead.paperclipmobile.domain.IssueRef? = null) =
    RunRowUi(
        id.value,
        agentName,
        issueId?.value,
        status.toUi(),
        startedAt?.toEpochMilliseconds(),
        createdAt.toEpochMilliseconds(),
        finishedAt?.toEpochMilliseconds(),
        issue?.identifier,
        issue?.title,
    )

/** What a mutation gave back: feedback for the screen, and the entity to show when the server's truth is known. */
internal data class ActionResult<T>(
    val feedback: FeedbackUi?,
    val value: T?,
    /** The role resolved again after a 403, so the screen can show the new role's actions. */
    val role: Role? = null,
    /** The call may have changed the server although there is no entity to show: the screen should reload. */
    val reload: Boolean = false,
)

/** Resolves the person's role once, then runs one mutation use case and turns its result into screen terms. */
internal suspend fun <T> mutate(
    context: ContextSource,
    call: suspend (CompanyContext) -> MutationResult<T>,
): ActionResult<T> =
    when (val resolved = context.get()) {
        is DomainResult.Failure -> {
            ActionResult(FeedbackUi(FeedbackKind.Failed, error = resolved.error.toErrorUi()), null)
        }

        is DomainResult.Success -> {
            when (val result = call(resolved.value)) {
                is MutationResult.Done -> ActionResult(null, result.value)
                is MutationResult.Reconciled -> ActionResult(result.toFeedback(), result.current)
                is MutationResult.Unknown -> ActionResult(result.toFeedback(), null, reload = true)
                else -> ActionResult(result.toFeedback(), null, role = reResolved(context, result))
            }
        }
    }

/** After a 403 the person's role is read once more; any other result leaves it alone. */
private suspend fun reResolved(
    context: ContextSource,
    result: MutationResult<*>,
): Role? = if (result.wasForbidden()) context.reResolve()?.role else null

/** A reference to an issue shown as a link: a parent, a blocker, an agent's current issue. */
data class IssueRefUi(
    val id: String,
    val identifier: String,
    val title: String,
    val status: IssueStatusUi,
)

internal fun com.stainedhead.paperclipmobile.domain.IssueRef.toUi() =
    IssueRefUi(id.value, identifier, title, status.toUi())
