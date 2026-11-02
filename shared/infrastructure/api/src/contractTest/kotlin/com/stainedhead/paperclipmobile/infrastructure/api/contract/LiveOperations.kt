package com.stainedhead.paperclipmobile.infrastructure.api.contract

/** Every port method of the gateways: the run is judged against this list (F-C-2, F-C-3). */
internal val OPERATIONS: List<String> =
    listOf(
        "HealthGateway.probe",
        "AuthGateway.createChallenge",
        "AuthGateway.pollChallenge",
        "AuthGateway.meWithKey",
        "AuthGateway.me",
        "AuthGateway.listCompanies",
        "AuthGateway.listBoardKeys",
        "AuthGateway.listBoardKeysWithKey",
        "AuthGateway.revokeBoardKey",
        "AuthGateway.revokeCurrentKey",
        "AttentionGateway.feed",
        "AttentionGateway.dismiss",
        "AttentionGateway.snooze",
        "AttentionGateway.restore",
        "AttentionGateway.setDecideBy",
        "DecisionGateway.get",
        "DecisionGateway.decide",
        "DecisionGateway.dismiss",
        "ApprovalGateway.get",
        "ApprovalGateway.linkedIssues",
        "ApprovalGateway.comments",
        "ApprovalGateway.approve",
        "ApprovalGateway.reject",
        "ApprovalGateway.requestRevision",
        "ApprovalGateway.addComment",
        "BudgetGateway.incident",
        "BudgetGateway.resolve",
        "IssueGateway.list",
        "IssueGateway.get",
        "IssueGateway.comments",
        "IssueGateway.addComment",
        "IssueGateway.setStatus",
        "IssueGateway.assign",
        "IssueGateway.create",
        "IssueGateway.attachments",
        "InteractionGateway.list",
        "InteractionGateway.answer",
        "AgentGateway.list",
        "AgentGateway.get",
        "AgentGateway.pause",
        "AgentGateway.resume",
        "AgentGateway.invokeHeartbeat",
        "AgentGateway.setBudget",
        "RunGateway.get",
        "RunGateway.events",
        "RunGateway.log",
        "RunGateway.cancel",
        "RunGateway.forIssue",
        "RunGateway.forAgent",
        "RunGateway.live",
        "DashboardGateway.dashboard",
        "DashboardGateway.windowSpend",
    )

/**
 * Operations that may stay out of the live-with-data count, each with the reason. Everything else must run against
 * the live server and decode a non-empty reply, or the run fails. An entry is a statement about the server, not
 * about test effort: remove it when the data can be seeded.
 */
internal val SKIP_ALLOWED: Map<String, String> =
    mapOf(
        "DecisionGateway.get" to DECISIONS_AGENT_ONLY,
        "DecisionGateway.decide" to DECISIONS_AGENT_ONLY,
        "DecisionGateway.dismiss" to DECISIONS_AGENT_ONLY,
    )

/** Operations whose reply may stay empty on the throwaway server, each with the reason. */
internal val EMPTY_ALLOWED: Map<String, String> = emptyMap()

private const val DECISIONS_AGENT_ONLY =
    "decisions are created only inside an agent run: POST /api/companies/{c}/decisions answers 403 " +
        "'Agent run context required' to a board key (pinned dist routes/decisions.js)"
