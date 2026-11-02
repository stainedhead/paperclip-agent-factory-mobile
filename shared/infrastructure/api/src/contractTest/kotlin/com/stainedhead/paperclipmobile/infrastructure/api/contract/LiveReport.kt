package com.stainedhead.paperclipmobile.infrastructure.api.contract

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects which gateway operations the contract run exercised against the live server (AD-050). Every port method
 * is listed in [OPERATIONS]; one that is not marked [verified] is covered only by the MockEngine tests, and the run
 * prints that gap list at the end.
 */
internal object LiveReport {
    val OPERATIONS: List<String> =
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

    private val verified = ConcurrentHashMap<String, String>()
    private val attemptsFailed = ConcurrentHashMap<String, String>()
    private val notes = ConcurrentHashMap<String, String>()

    fun verified(
        operation: String,
        detail: String = "",
    ) {
        require(OPERATIONS.contains(operation)) { "Unknown operation $operation" }
        verified[operation] = detail
        attemptsFailed.remove(operation)
    }

    /** An operation that was tried against the live server and did not behave as the adapter assumes. */
    fun failed(
        operation: String,
        reason: String,
    ) {
        require(OPERATIONS.contains(operation)) { "Unknown operation $operation" }
        if (!verified.containsKey(operation)) attemptsFailed[operation] = reason
    }

    /** A fact about the live server worth keeping in the run output (what the stub did not know). */
    fun note(
        topic: String,
        text: String,
    ) {
        notes[topic] = text
    }

    fun render(): String {
        if (verified.isEmpty() &&
            attemptsFailed.isEmpty()
        ) {
            return "\nPaperclip live contract run: no server was used.\n"
        }
        val out = StringBuilder("\n===== Paperclip live contract run: operations =====\n")
        out.append("Verified against the live server: ${verified.size} of ${OPERATIONS.size}\n")
        OPERATIONS.filter { verified.containsKey(it) }.forEach {
            out.append("  [live]  $it ${verified[it].orEmpty()}\n".trimEnd() + "\n")
        }
        val tried = OPERATIONS.filter { attemptsFailed.containsKey(it) }
        if (tried.isNotEmpty()) out.append("Tried live and failed (adapter or seed differs from the server):\n")
        tried.forEach { out.append("  [FAIL]  $it: ${attemptsFailed[it]}\n") }
        val rest = OPERATIONS.filter { !verified.containsKey(it) && !attemptsFailed.containsKey(it) }
        out.append("MockEngine only (not exercised live in this run):\n")
        rest.forEach { out.append("  [mock]  $it\n") }
        if (notes.isNotEmpty()) out.append("Observed on the live server:\n")
        notes.toSortedMap().forEach { (topic, text) -> out.append("  - $topic: $text\n") }
        return out.toString()
    }

    fun write(reportDir: String?) {
        val text = render()
        println(text)
        reportDir?.let { dir ->
            File(dir).mkdirs()
            File(dir, "live-operations.txt").writeText(text)
        }
    }
}
