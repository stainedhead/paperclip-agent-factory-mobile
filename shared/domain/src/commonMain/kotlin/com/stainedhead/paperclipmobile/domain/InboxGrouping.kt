package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

data class InboxSection(
    val group: InboxGroup,
    val items: List<AttentionItem>,
)

object InboxGrouping {
    /**
     * Groups by `decideBy`; severity orders items inside a group only, and the server's order breaks ties (PRD FR-030).
     * Empty groups are omitted.
     */
    fun group(
        items: List<AttentionItem>,
        now: Instant,
    ): List<InboxSection> {
        val byGroup = items.groupBy { AttentionRules.group(it.decideBy, now) }
        return InboxGroup.entries.mapNotNull { group ->
            byGroup[group]?.let { InboxSection(group, it.sortedBy { item -> item.severity.rank }) }
        }
    }
}
