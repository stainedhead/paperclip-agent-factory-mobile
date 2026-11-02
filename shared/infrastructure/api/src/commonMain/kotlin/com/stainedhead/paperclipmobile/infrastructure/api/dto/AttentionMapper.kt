package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionVerb
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DismissalKind
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull

/**
 * The page as a domain value, or null (so Malformed) when the page or any one card lacks what identifies it. A card is
 * never skipped: dropping one would make the page disagree with its own `totalCount` and hide something that needs a
 * decision (ADR-059). New enum values never fail: an unknown kind becomes `Unsupported`, an unknown severity Medium.
 */
internal fun AttentionFeedDto.toDomain(): AttentionFeed? {
    val generated = parseInstantOrNull(generatedAt) ?: return null
    val cards = (items ?: return null).map { it.toDomain() }
    if (cards.any { it == null } || totalCount == null || deskBadgeCount == null) return null
    return AttentionFeed(
        generatedAt = generated,
        totalCount = totalCount,
        deskBadgeCount = deskBadgeCount,
        nextCursor = nextCursor?.takeIf { it.isNotBlank() },
        countsBySourceKind = countsBySourceKind.orEmpty().mapKeys { AttentionSourceKind.fromServer(it.key) },
        items = cards.filterNotNull(),
    )
}

internal fun AttentionItemDto.toDomain(): AttentionItem? {
    val itemId = id?.takeIf { it.isNotBlank() } ?: return null
    val kind = sourceKind?.takeIf { it.isNotBlank() } ?: return null
    val key = dismissalKey?.takeIf { it.isNotBlank() } ?: return null
    val about = subject?.toDomain() ?: return null
    return AttentionItem(
        id = itemId,
        sourceKind = AttentionSourceKind.fromServer(kind),
        subject = about,
        whyNow = whyNow.orEmpty(),
        severity = Severity.fromServer(severity),
        dismissalKey = key,
        activityAt = parseInstantOrNull(activityAt) ?: return null,
        createdAt = parseInstantOrNull(createdAt) ?: return null,
        decideBy = DecideBy.parse(decideBy),
        decisionVerbs = decisionVerbs.orEmpty().mapNotNull { it.toDomain() },
        inlineResolvable = inlineResolvable == true,
        dismissal = dismissal?.toDomain(),
        snoozedUntil = parseInstantOrNull(snoozedUntil),
        originAgentName = originAgentName?.takeIf { it.isNotBlank() },
        relatedIssue = relatedIssue?.toDomain(),
        detail = detail?.let { attentionDetail(it) },
        resolverAudience = resolverAudience?.let { resolverAudienceOf(it) },
    )
}

private fun DecisionVerbDto.toDomain(): DecisionVerb? {
    if (id.isNullOrBlank() || label.isNullOrBlank()) return null
    return DecisionVerb(id, label, description?.takeIf { it.isNotBlank() })
}

/** A dismissal the server reports; one without a readable time is ignored rather than guessed. */
private fun ItemDismissalDto.toDomain(): Dismissal? {
    val at = parseInstantOrNull(dismissedAt) ?: return null
    return Dismissal(dismissalKind(kind), at, parseInstantOrNull(snoozedUntil), isActive == true)
}

internal fun dismissalKind(raw: String?): DismissalKind =
    if (raw ==
        "snooze"
    ) {
        DismissalKind.Snooze
    } else {
        DismissalKind.Dismiss
    }

/**
 * The answer of `POST .../inbox-dismissals`. The row has no evaluation, so it is active at once: a dismissal has just
 * been made, and a snooze is only accepted for a future time (routes/inbox-dismissals.ts:24-34).
 */
internal fun DismissalRowDto.toDomain(): Dismissal? {
    val at = parseInstantOrNull(dismissedAt) ?: return null
    return Dismissal(dismissalKind(kind), at, parseInstantOrNull(snoozedUntil), isActive = true)
}
