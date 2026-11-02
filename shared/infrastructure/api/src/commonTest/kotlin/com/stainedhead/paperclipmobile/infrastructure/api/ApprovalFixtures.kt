package com.stainedhead.paperclipmobile.infrastructure.api

/**
 * Approval bodies in the shapes of the pinned server, written from source (not recorded): the approval row with its
 * payload redacted (`routes/approvals.ts:28-33,224-230`, columns `packages/db/src/schema/approvals.ts:8-22`), a comment
 * row (`approval_comments.ts:6-18`, 201 at `approvals.ts:517-543`) and the issue rows of `listIssuesForApproval`
 * (`services/issue-approvals.ts:74-110`). Payload fields: hire_agent `routes/agents.ts:4427-4437,4663-4680`; the other
 * three `ui/src/components/ApprovalPayload.tsx:89-184` and `services/budgets.ts:253-269`.
 *
 * Re-verified in 1b-4b against the pinned release 2026.1023.0 (compiled dist): `server/dist/routes/approvals.js:30-58,
 * 180-476`, `lib/approval-records.js`, `services/approvals.js`; payloads are redacted by `redactEventPayload`.
 */
internal object ApprovalFixtures {
    const val HIRE_ID = "11111111-1111-4111-8111-111111111111"

    fun approval(row: StubApproval): String {
        val agent = if (row.requestedByAgentId == null) "null" else "\"${row.requestedByAgentId}\""
        val note = if (row.decisionNote == null) "null" else "\"${row.decisionNote}\""
        return """
            {"id":"${row.id}","companyId":"${row.company}","type":"${row.type}","requestedByAgentId":$agent,
             "requestedByUserId":null,"status":"${row.status}","payload":${row.payload},"decisionNote":$note,
             "decidedByUserId":null,"decidedAt":null,"createdAt":"2026-11-01T08:00:00.000Z",
             "updatedAt":"2026-11-01T08:00:00.000Z","somethingNew":{"a":1}}
            """.trimIndent()
    }

    fun comment(row: StubComment) =
        """
        {"id":"${row.id}","companyId":"${row.company}","approvalId":"${row.approvalId}","authorAgentId":null,
         "authorUserId":"user-1","body":"${row.body}","createdAt":"${row.createdAt}","updatedAt":"${row.createdAt}"}
        """.trimIndent()

    fun issue(
        id: String,
        identifier: String?,
        title: String,
    ): String {
        val ident = if (identifier == null) "null" else "\"$identifier\""
        return """
            {"id":"$id","companyId":"company-acme","title":"$title","description":null,"status":"in_review",
             "priority":"medium","identifier":$ident,"issueNumber":1182,"createdAt":"2026-11-01T07:00:00.000Z"}
            """.trimIndent()
    }

    val hirePayload =
        """
        {"name":"Designer","role":"designer","title":"Product Designer","icon":"palette","capabilities":"Figma",
         "reportsTo":"agent-cto","adapterType":"claude_local","adapterConfig":{},"runtimeConfig":{},
         "desiredSkills":["figma","research"],"budgetMonthlyCents":20000,"metadata":{}}
        """.trimIndent()

    const val CEO_PAYLOAD = """{"title":"Q4 strategy","description":"Focus on billing."}"""

    val budgetPayload =
        """
        {"scopeType":"agent","scopeName":"Engineer 2","metric":"billed_cents","windowKind":"calendar_month_utc",
         "thresholdType":"hard","budgetAmount":10000,"observedAmount":10000.5,"observedAmountExact":"10000.5000000",
         "guidance":"Raise the budget or keep the scope paused."}
        """.trimIndent()

    val boardPayload =
        """
        {"title":"Switch the CI provider","summary":"Cheaper runners.","recommendedAction":"Approve",
         "nextActionOnApproval":"Engineer 1 migrates.","risks":["- Downtime tonight","Learning curve"],
         "proposedComment":"Go."}
        """.trimIndent()
}
