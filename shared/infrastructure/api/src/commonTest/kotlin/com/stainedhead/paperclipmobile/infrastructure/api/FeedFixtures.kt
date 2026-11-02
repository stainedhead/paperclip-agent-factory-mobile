package com.stainedhead.paperclipmobile.infrastructure.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Attention feed bodies in the shapes of the pinned server, written from source (not recorded): the item as
 * `createItem` builds it (`services/attention.ts:393-420`), subjects and metadata per source kind (`:1138-1152`,
 * `:1257-1270`, `:1772-1790`), details (`packages/shared/src/types/attention.ts:110-187`), the resolver audience
 * (`:189-219`, `services/attention.ts:738-770`) and the page (`:221-277`, `:1893-1937`).
 */
internal object FeedFixtures {
    val SOURCE_KINDS =
        listOf(
            "approval",
            "decision",
            "issue_thread_interaction",
            "join_request",
            "recovery_action",
            "productivity_review",
            "blocker_attention",
            "review",
            "failed_run",
            "budget_alert",
            "agent_error_alert",
        )

    const val ACTIVITY_AT = "2026-11-01T10:00:00.000Z"
    const val CREATED_AT = "2026-11-01T08:00:00.000Z"

    fun subject(
        kind: String,
        id: String,
        title: String,
        metadata: JsonObject = JsonObject(emptyMap()),
    ) = buildJsonObject {
        put("kind", kind)
        put("id", id)
        put("companyId", ServerFixtures.COMPANY_ACME)
        put("title", title)
        put("identifier", JsonNull)
        put("status", "pending")
        put("href", "/ACM/${kind}s/$id")
        put("metadata", metadata)
    }

    fun item(
        sourceKind: String,
        dedupKey: String,
        subject: JsonObject,
        detail: JsonObject? = null,
        decideBy: String? = null,
    ): JsonObject =
        buildJsonObject {
            put("id", "$sourceKind:$dedupKey")
            put("companyId", ServerFixtures.COMPANY_ACME)
            put("sourceKind", sourceKind)
            put("subject", subject)
            put("whyNow", "Why now for $dedupKey.")
            put("decisionVerbs", verbs())
            put("inlineResolvable", true)
            put("entryRule", "status = 'pending'")
            put("exitRule", "leaves pending")
            put("dedupKey", dedupKey)
            put("dismissalKey", "attention:$dedupKey")
            put("dismissal", JsonNull)
            put("severity", "medium")
            put("rank", 1)
            put("activityAt", ACTIVITY_AT)
            put("createdAt", CREATED_AT)
            put("updatedAt", ACTIVITY_AT)
            put("relatedIssue", JsonNull)
            put("project", JsonNull)
            put("originAgentName", "CEO")
            put("queues", JsonArray(emptyList()))
            put("shelf", false)
            put("decideBy", decideBy?.let { JsonPrimitive(it) } ?: JsonNull)
            put("snoozedUntil", JsonNull)
            put("detail", detail ?: JsonNull)
            put("somethingNew", JsonObject(emptyMap()))
        }

    private fun verbs() =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("id", "approve")
                    put("label", "Approve")
                    put("description", "Approve it.")
                },
            )
            add(
                buildJsonObject {
                    put("id", "reject")
                    put("label", "Reject")
                    put("description", JsonNull)
                },
            )
        }

    fun with(
        item: JsonObject,
        vararg changes: Pair<String, JsonElement>,
    ) = JsonObject(item + changes)

    fun approvalItem(id: String = "ap-1") =
        item(
            "approval",
            "approval:$id",
            subject(
                "approval",
                id,
                "Hire a Designer",
                buildJsonObject {
                    put("type", "hire_agent")
                    put("requestedByAgentId", "agent-ceo")
                    put("issueId", "issue-1")
                },
            ),
            detail("approval", "approvalType" to "hire_agent", "summaryExcerpt" to "Hire a Designer"),
            decideBy = "today",
        )

    fun decisionItem(id: String = "dec-1") =
        item(
            "decision",
            "decision:$id",
            subject("decision", id, "Ship the billing migration?"),
            detail("generic", "summaryExcerpt" to "Ship tonight or hold."),
            decideBy = "this_week",
        )

    fun budgetItem(id: String = "inc-1") =
        item(
            "budget_alert",
            "budget:policy-1:2026-11-01T00:00:00.000Z:hard",
            subject(
                "budget_incident",
                id,
                "Engineer 2 budget hard stop",
                buildJsonObject {
                    put("policyId", "policy-1")
                    put("scopeType", "agent")
                    put("thresholdType", "hard")
                    put("amountObserved", JsonPrimitive(10_000.5))
                    put("amountLimit", JsonPrimitive(10_000))
                },
            ),
            buildJsonObject {
                put("kind", "budget")
                put("observedPercent", JsonPrimitive(100.01))
                put("amountObserved", JsonPrimitive(10_000.5))
                put("amountLimit", JsonPrimitive(10_000))
                put("images", JsonArray(emptyList()))
            },
            decideBy = "2026-11-02",
        )

    fun interactionItem(id: String = "int-1") =
        with(
            item(
                "issue_thread_interaction",
                "interaction:$id",
                subject(
                    "interaction",
                    id,
                    "Questions need answers",
                    buildJsonObject {
                        put("kind", "ask_user_questions")
                        put("issueId", "issue-1182")
                    },
                ),
                detail("questions", "questionCount" to 2, "firstQuestionText" to "Which database?"),
            ),
            "resolverAudience" to
                buildJsonObject {
                    put("requestedResolverPolicy", "not_creator")
                    put("effectiveResolverPolicy", "human_only")
                    put("effectiveResolverPolicySource", "company_cap")
                    put("resolverPolicyProvenance", "explicit")
                    put("addresseeAgentId", JsonNull)
                    put("addresseeUserId", "user-2")
                    put("addresseeName", JsonNull)
                    put("createdByAgentId", "agent-eng-1")
                    put("createdByAgentName", "Engineer 1")
                },
        )

    fun failedRunItem(id: String = "run-1") =
        item(
            "failed_run",
            "run:$id",
            subject("run", id, "Run failed"),
            detail("failed_run", "agentName" to "Engineer 1", "failureReasonExcerpt" to "Out of memory"),
        )

    /** Five cards of five kinds, the standard feed of the contract runs. */
    fun standard(): List<JsonObject> =
        listOf(approvalItem(), decisionItem(), budgetItem(), interactionItem(), failedRunItem())

    fun detail(
        kind: String,
        vararg fields: Pair<String, Any?>,
    ) = buildJsonObject {
        put("kind", kind)
        fields.forEach { (name, value) ->
            when (value) {
                null -> put(name, JsonNull)
                is Int -> put(name, value)
                else -> put(name, value.toString())
            }
        }
        put("images", JsonArray(emptyList()))
    }

    /** A page; the counts are over [company], the whole filtered feed, as the server computes them before paging. */
    fun page(
        items: List<JsonObject>,
        nextCursor: String? = null,
        company: List<JsonObject> = items,
        badge: Int = 2,
    ): String =
        buildJsonObject {
            put("companyId", ServerFixtures.COMPANY_ACME)
            put("generatedAt", "2026-11-01T12:00:00.000Z")
            put("totalCount", company.size)
            put("deskBadgeCount", badge)
            put("nextCursor", nextCursor?.let { JsonPrimitive(it) } ?: JsonNull)
            put("countsBySourceKind", JsonObject(SOURCE_KINDS.associateWith { JsonPrimitive(countOf(company, it)) }))
            put("items", JsonArray(items))
        }.toString()

    private fun countOf(
        items: List<JsonObject>,
        kind: String,
    ) = items.count { (it["sourceKind"] as JsonPrimitive).content == kind }
}
