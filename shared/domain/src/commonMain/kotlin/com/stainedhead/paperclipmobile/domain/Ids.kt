package com.stainedhead.paperclipmobile.domain

/**
 * App-generated identity of one configured factory (a Paperclip instance). It is a path segment of every secret
 * (`instance/{id}/{name}`), so it cannot hold a slash, a backslash or a control character: an id such as `a/b` would
 * otherwise be listed by the secret store as the namespace `a` (rereview1b F-R-6).
 */
data class InstanceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "InstanceId must not be blank" }
        require(value.none { it == '/' || it == '\\' || it.isISOControl() }) {
            "InstanceId must not hold a separator or a control character"
        }
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

/** Server identity of an issue. */
data class IssueId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "IssueId must not be blank" }
    }
}

/** Server identity of an agent. */
data class AgentId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "AgentId must not be blank" }
    }
}

/** Server identity of an run. */
data class RunId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "RunId must not be blank" }
    }
}

/** Server identity of an interaction. */
data class InteractionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "InteractionId must not be blank" }
    }
}
