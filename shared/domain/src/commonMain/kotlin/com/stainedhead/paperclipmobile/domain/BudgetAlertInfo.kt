package com.stainedhead.paperclipmobile.domain

enum class BudgetThreshold {
    Hard,
    Soft,
}

/** The typed view of a `budget_alert` item's subject metadata (research Q2): `thresholdType` is only there. */
data class BudgetAlertInfo(
    val threshold: BudgetThreshold,
    val observed: Money,
    val limit: Money,
    val scopeType: String? = null,
)
