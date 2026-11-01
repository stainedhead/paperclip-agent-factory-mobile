package com.stainedhead.paperclipmobile.domain

enum class IncidentStatus {
    Open,
    Resolved,
    Dismissed,
    Unknown,
    ;

    companion object {
        fun fromServer(raw: String): IncidentStatus =
            when (raw) {
                "open" -> Open
                "resolved" -> Resolved
                "dismissed" -> Dismissed
                else -> Unknown
            }
    }
}

/** What the person chose for a budget incident (PRD FR-052). */
sealed interface BudgetResolution {
    data object KeepPaused : BudgetResolution

    data class RaiseAndResume(
        val amount: Money,
    ) : BudgetResolution
}

enum class ResolutionIssue {
    MustExceedObserved,
    WrongCurrency,
}

/** An open budget stop or warning. [observed] is rounded up to whole cents by the adapter (ADR-016). */
data class BudgetIncident(
    val id: BudgetIncidentId,
    val scopeName: String,
    val scopeType: String,
    val threshold: BudgetThreshold,
    val observed: Money,
    val limit: Money,
    val status: IncidentStatus,
    val approvalId: ApprovalId? = null,
) {
    val isOpen: Boolean get() = status == IncidentStatus.Open

    /** The client-side pre-check; the server recomputes spend and decides (research Q5). */
    fun validate(resolution: BudgetResolution): ResolutionIssue? =
        when {
            resolution !is BudgetResolution.RaiseAndResume -> null
            resolution.amount.currency != observed.currency -> ResolutionIssue.WrongCurrency
            resolution.amount <= observed -> ResolutionIssue.MustExceedObserved
            else -> null
        }
}
