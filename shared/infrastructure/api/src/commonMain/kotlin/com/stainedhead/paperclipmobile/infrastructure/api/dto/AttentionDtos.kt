package com.stainedhead.paperclipmobile.infrastructure.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Shapes from packages/shared/src/types/attention.ts:221-277 and server/src/services/attention.ts (research, feed).
// Everything is optional here: the mapper decides what a feed item cannot do without (ADR-059).

/** `GET /companies/{c}/attention`. Counts are company-wide and computed before paging. */
@Serializable
internal data class AttentionFeedDto(
    val generatedAt: String? = null,
    val totalCount: Int? = null,
    val deskBadgeCount: Int? = null,
    val nextCursor: String? = null,
    val countsBySourceKind: Map<String, Int>? = null,
    val items: List<AttentionItemDto>? = null,
)

/** One feed card. `detail` and `resolverAudience` are read by hand because their shape depends on the kind. */
@Serializable
internal data class AttentionItemDto(
    val id: String? = null,
    val sourceKind: String? = null,
    val subject: AttentionSubjectDto? = null,
    val whyNow: String? = null,
    val decisionVerbs: List<DecisionVerbDto>? = null,
    val inlineResolvable: Boolean? = null,
    val dismissalKey: String? = null,
    val dismissal: ItemDismissalDto? = null,
    val severity: String? = null,
    val activityAt: String? = null,
    val createdAt: String? = null,
    val decideBy: String? = null,
    val snoozedUntil: String? = null,
    val originAgentName: String? = null,
    val relatedIssue: AttentionSubjectDto? = null,
    val detail: JsonObject? = null,
    val resolverAudience: JsonObject? = null,
)

/** What a card is about. `metadata` holds the typed extras (approval type, interaction kind, budget amounts). */
@Serializable
internal data class AttentionSubjectDto(
    val kind: String? = null,
    val id: String? = null,
    val title: String? = null,
    val identifier: String? = null,
    val status: String? = null,
    val href: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
internal data class DecisionVerbDto(
    val id: String? = null,
    val label: String? = null,
    val description: String? = null,
)

/** `AttentionItemDismissal` (types/attention.ts:93-98): how the server evaluated this person's dismissal. */
@Serializable
internal data class ItemDismissalDto(
    val kind: String? = null,
    val dismissedAt: String? = null,
    val snoozedUntil: String? = null,
    val isActive: Boolean? = null,
)

/** The row `POST .../inbox-dismissals` answers with 201 (services/inbox-dismissals.ts:5-35). */
@Serializable
internal data class DismissalRowDto(
    val kind: String? = null,
    val dismissedAt: String? = null,
    val snoozedUntil: String? = null,
)
