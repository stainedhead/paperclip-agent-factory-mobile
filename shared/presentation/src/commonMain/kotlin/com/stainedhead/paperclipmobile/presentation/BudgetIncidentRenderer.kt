package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.ResolutionIssue
import com.stainedhead.paperclipmobile.domain.Role
import kotlin.time.Instant

/** Everything the Budget incident sheet knows, before it is rendered into [BudgetIncidentState]. */
internal data class BudgetModel(
    val context: CompanyContext? = null,
    val incident: BudgetIncident? = null,
    val outcome: BudgetOutcomeUi? = null,
    val choice: BudgetChoice? = null,
    val amount: Long? = null,
    val loadedAt: Instant? = null,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

private const val PERCENT = 100

/** Holds the Budget incident sheet's display rules (PRD FR-052). */
internal object BudgetIncidentRenderer {
    fun render(
        model: BudgetModel,
        online: Boolean,
    ): BudgetIncidentState {
        val incident = model.incident
        val role = model.context?.role
        val open = incident != null && incident.isOpen && model.outcome == null
        val actor = role.takeIf { open && online && !model.submitting }
        val canKeep = actor != null && Permissions.allows(actor, Action.KeepBudgetPaused)
        val canRaise = actor != null && Permissions.allows(actor, Action.RaiseBudgetAndResume)
        val issue = amountIssue(model)
        return BudgetIncidentState(
            phase = phase(incident != null || model.outcome != null, model.loading),
            scopeName = incident?.scopeName.orEmpty(),
            scopeType = incident?.scopeType.orEmpty(),
            threshold = incident?.threshold?.let { BudgetThresholdUi.entries[it.ordinal] },
            observed = incident?.observed?.toUi(),
            limit = incident?.limit?.toUi(),
            observedPercent = percent(incident),
            outcome = model.outcome,
            choice = model.choice,
            amountMinorUnits = model.amount,
            amountIssue = issue,
            canKeepPaused = canKeep,
            canRaise = canRaise,
            canSubmit = canSubmit(model, canKeep, canRaise, issue),
            submitNeedsDeviceAuth = model.choice == BudgetChoice.RaiseAndResume,
            isSubmitting = model.submitting,
            isOffline = !online,
            actionsDisabledReason = disabledReason(online, role),
            role = role?.toUi(),
            updatedAtEpochMillis = model.loadedAt?.toEpochMilliseconds(),
            error = model.error,
            feedback = model.feedback,
        )
    }

    private fun phase(
        hasContent: Boolean,
        loading: Boolean,
    ) = when {
        hasContent -> DetailPhase.Content
        loading -> DetailPhase.Loading
        else -> DetailPhase.Failed
    }

    private fun canSubmit(
        model: BudgetModel,
        canKeep: Boolean,
        canRaise: Boolean,
        issue: InvalidKey?,
    ) = when (model.choice) {
        BudgetChoice.KeepPaused -> canKeep
        BudgetChoice.RaiseAndResume -> canRaise && model.amount != null && issue == null
        null -> false
    }

    private fun percent(incident: BudgetIncident?): Int =
        if (incident == null ||
            incident.limit.minorUnits == 0L
        ) {
            0
        } else {
            (incident.observed.minorUnits * PERCENT / incident.limit.minorUnits).toInt()
        }

    private fun amountIssue(model: BudgetModel): InvalidKey? {
        val incident = model.incident
        val amount = model.amount
        if (model.choice != BudgetChoice.RaiseAndResume || incident == null || amount == null) return null
        return when (incident.validate(BudgetResolution.RaiseAndResume(Money(amount, incident.observed.currency)))) {
            ResolutionIssue.MustExceedObserved -> InvalidKey.AmountMustExceedObserved
            ResolutionIssue.WrongCurrency -> InvalidKey.WrongCurrency
            null -> null
        }
    }

    private fun disabledReason(
        online: Boolean,
        role: Role?,
    ) = when {
        !online -> ActionsDisabledReason.Offline
        role == Role.Viewer -> ActionsDisabledReason.ViewerRole
        else -> null
    }

    /** How a status that is no longer open reads; null while the incident is still open. */
    fun outcomeOf(status: IncidentStatus): BudgetOutcomeUi? =
        when (status) {
            IncidentStatus.Open -> null
            IncidentStatus.Resolved -> BudgetOutcomeUi.Raised
            IncidentStatus.Dismissed -> BudgetOutcomeUi.KeptPaused
            IncidentStatus.Unknown -> BudgetOutcomeUi.NoLongerActive
        }
}
