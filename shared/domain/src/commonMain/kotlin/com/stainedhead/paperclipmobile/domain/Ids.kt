package com.stainedhead.paperclipmobile.domain

/** App-generated identity of one configured factory (a Paperclip instance). */
data class InstanceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "InstanceId must not be blank" }
    }
}

/** Server identity of a company inside one instance. */
data class CompanyId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CompanyId must not be blank" }
    }
}

/** Server identity of a decision. */
data class DecisionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "DecisionId must not be blank" }
    }
}

/** Server identity of an approval. */
data class ApprovalId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ApprovalId must not be blank" }
    }
}

/** Server identity of a budget incident. */
data class BudgetIncidentId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "BudgetIncidentId must not be blank" }
    }
}
