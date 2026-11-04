package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionRules
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DismissalKind
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A scripted Attention endpoint that behaves like the server: it serves [items] in order with the server's cursor
 * rules, hides dismissed and snoozed items, and applies decide-by changes. Put an error in [failures] under the
 * operation name (`feed`, `dismiss`, `snooze`, `restore`, `setDecideBy`) to make that call fail.
 */
class FakeAttentionGateway(
    private val clock: Clock,
    var items: List<AttentionItem> = AttentionFixtures.feed().items,
) : AttentionGateway {
    val calls = mutableMapOf<String, Int>()

    /** Makes every feed read take this long, so screens can be observed while loading. */
    var feedLatencyMillis = 0L

    /** When true the feed ignores dismissals, like a read that was computed before the dismissal. */
    var servesStaleFeed = false
    val failures = mutableMapOf<String, DomainError>()
    val queries = mutableListOf<FeedQuery>()
    val snoozes = mutableListOf<Pair<String, Instant>>()
    val decideByCalls = mutableListOf<Triple<AttentionSourceKind, String, DecideBy>>()
    private val hidden = mutableMapOf<String, Dismissal>()

    /** Companies (by id) whose feed is empty: nothing there needs the person. Every other company gets [items]. */
    var emptyCompanies: Set<String> = emptySet()
    private val resolvedSubjects = mutableSetOf<String>()
    private var served = 0

    /** How old the first feed read says it is (`generatedAt` is that long before now); later reads are current. */
    var firstFeedAge: Duration = Duration.ZERO

    /** The subject (an approval, an incident) was resolved elsewhere: the server's feed stops returning its card. */
    fun resolveSubject(subjectId: String) {
        resolvedSubjects += subjectId
    }

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun feed(
        scope: CompanyScope,
        query: FeedQuery,
    ): DomainResult<AttentionFeed> {
        queries += query
        if (feedLatencyMillis > 0) delay(feedLatencyMillis)
        record("feed")?.let { return DomainResult.Failure(it) }
        val now = clock.now()
        val generatedAt = if (served++ == 0) now - firstFeedAge else now
        val visible =
            items.filter { scope.companyId.value !in emptyCompanies }.filter {
                it.subject.id !in resolvedSubjects &&
                    (servesStaleFeed || it.dismissalKey !in hidden)
            }
        val start =
            query.cursor?.let { cursor ->
                val index = visible.indexOfFirst { it.id == cursor }
                if (index <
                    0
                ) {
                    return DomainResult.Failure(
                        DomainError.Rejected("Attention cursor no longer matches the filtered feed"),
                    )
                }
                index + 1
            } ?: 0
        val page = visible.drop(start).take(query.limit)
        val next = if (start + page.size < visible.size) page.last().id else null
        return DomainResult.Success(
            AttentionFeed(
                generatedAt = generatedAt,
                totalCount = visible.size,
                deskBadgeCount =
                    visible.count {
                        AttentionRules.isNewToday(it.createdAt, now) || AttentionRules.isDecideNow(it.decideBy, now)
                    },
                nextCursor = next,
                countsBySourceKind = visible.groupingBy { it.sourceKind }.eachCount(),
                items = page,
            ),
        )
    }

    override suspend fun dismiss(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Dismissal> {
        record("dismiss")?.let { return DomainResult.Failure(it) }
        val dismissal = Dismissal(DismissalKind.Dismiss, clock.now(), null, true)
        hidden[itemKey] = dismissal
        return DomainResult.Success(dismissal)
    }

    override suspend fun snooze(
        scope: CompanyScope,
        itemKey: String,
        until: Instant,
    ): DomainResult<Dismissal> {
        record("snooze")?.let { return DomainResult.Failure(it) }
        if (until <=
            clock.now()
        ) {
            return DomainResult.Failure(DomainError.Rejected("snoozedUntil must be in the future"))
        }
        snoozes += itemKey to until
        val dismissal = Dismissal(DismissalKind.Snooze, clock.now(), until, true)
        hidden[itemKey] = dismissal
        return DomainResult.Success(dismissal)
    }

    override suspend fun restore(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Unit> {
        record("restore")?.let { return DomainResult.Failure(it) }
        hidden.remove(itemKey)
        return DomainResult.Success(Unit)
    }

    override suspend fun setDecideBy(
        scope: CompanyScope,
        sourceKind: AttentionSourceKind,
        sourceId: String,
        decideBy: DecideBy,
    ): DomainResult<Unit> {
        record("setDecideBy")?.let { return DomainResult.Failure(it) }
        decideByCalls += Triple(sourceKind, sourceId, decideBy)
        items =
            items.map {
                if (it.sourceKind == sourceKind && it.subject.id == sourceId) it.copy(decideBy = decideBy) else it
            }
        return DomainResult.Success(Unit)
    }
}
