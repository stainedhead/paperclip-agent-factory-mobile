package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.CompanyContext
import kotlin.time.Instant

/** Everything the Inbox knows, before it is rendered into [InboxState]. */
internal data class InboxModel(
    val context: CompanyContext? = null,
    val baseUrl: String = "",
    val items: List<AttentionItem> = emptyList(),
    val counts: Map<AttentionSourceKind, Int> = emptyMap(),
    val badge: Int = 0,
    val total: Int = 0,
    val nextCursor: String? = null,
    val generatedAt: Instant? = null,
    val hasFeed: Boolean = false,
    val fromCache: Boolean = false,
    /** Ids hidden by a dismiss or snooze until the server's own feed stops returning them (ADR-017). */
    val hidden: Set<String> = emptySet(),
    val filter: SourceKindUi? = null,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val mutating: Boolean = false,
    val undo: UndoUi? = null,
    val rejectPrompt: RejectPromptUi? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
) {
    /** Replaces everything with a first page. The server's feed is the truth about what is still hidden. */
    fun withFirstPage(
        feed: AttentionFeed,
        fromCache: Boolean,
    ): InboxModel =
        copy(
            items = feed.items,
            counts = feed.countsBySourceKind,
            badge = feed.deskBadgeCount,
            total = feed.totalCount,
            nextCursor = feed.nextCursor,
            generatedAt = feed.generatedAt,
            hasFeed = true,
            fromCache = fromCache,
            hidden = if (fromCache) hidden else hidden.filter { id -> feed.items.any { it.id == id } }.toSet(),
            error = if (fromCache) error else null,
        )

    /** Appends a later page, skipping cards already shown. */
    fun withNextPage(feed: AttentionFeed): InboxModel {
        val known = items.map { it.id }.toSet()
        return copy(
            items = items + feed.items.filter { it.id !in known },
            counts = feed.countsBySourceKind,
            badge = feed.deskBadgeCount,
            total = feed.totalCount,
            nextCursor = feed.nextCursor,
            generatedAt = feed.generatedAt,
        )
    }

    fun find(itemId: String): AttentionItem? = items.firstOrNull { it.id == itemId }
}
