package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import com.stainedhead.paperclipmobile.infrastructure.persistence.payload.CachedFeed
import com.stainedhead.paperclipmobile.infrastructure.persistence.payload.toCached
import com.stainedhead.paperclipmobile.infrastructure.persistence.payload.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * [AttentionCache] over SQLite (AD-021): the last loaded Inbox page per factory and company, as one serialised row.
 *
 * - At most [MAX_ITEMS] items are kept. When a longer page is cut, the server's `nextCursor` no longer points at the
 *   first item that was dropped, so it is cleared instead of kept: a reader that pages on would skip items. The
 *   company-wide counts are kept, so the screen can still tell that the page is partial.
 * - A row that cannot be read (damaged, or written by a payload layout this version does not know) is a miss and is
 *   deleted; the next network load writes a fresh one.
 */
class SqlAttentionCache internal constructor(
    private val db: PaperclipDatabase,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AttentionCache {
    private val queries get() = db.attentionCacheQueries
    private val json = Json { encodeDefaults = true }

    override suspend fun read(scope: CompanyScope): AttentionFeed? =
        withContext(dispatcher) {
            val row = queries.selectOne(scope.instanceId.value, scope.companyId.value).executeAsOneOrNull()
            val feed = row?.takeIf { it.format == FORMAT }?.let { decodeOrNull(it.payload) }
            if (row != null && feed == null) clear(scope)
            feed
        }

    override suspend fun write(
        scope: CompanyScope,
        feed: AttentionFeed,
    ) {
        withContext(dispatcher) {
            val payload = json.encodeToString(CachedFeed.serializer(), feed.capped().toCached())
            queries.upsert(scope.instanceId.value, scope.companyId.value, payload, clock.now().toString(), FORMAT)
        }
    }

    override suspend fun clear(scope: CompanyScope) {
        withContext(dispatcher) { queries.deleteOne(scope.instanceId.value, scope.companyId.value) }
    }

    /** When the page of [scope] was cached, by the device clock, or null when nothing is cached. */
    suspend fun updatedAt(scope: CompanyScope): Instant? =
        withContext(dispatcher) {
            queries
                .selectOne(scope.instanceId.value, scope.companyId.value)
                .executeAsOneOrNull()
                ?.let { Instant.parse(it.updated_at) }
        }

    private fun decodeOrNull(payload: String): AttentionFeed? =
        try {
            json.decodeFromString(CachedFeed.serializer(), payload).toDomain()
        } catch (_: IllegalArgumentException) {
            // SerializationException and the domain's own validation both extend IllegalArgumentException.
            null
        }

    private fun AttentionFeed.capped(): AttentionFeed =
        if (items.size <= MAX_ITEMS) this else copy(items = items.take(MAX_ITEMS), nextCursor = null)

    private companion object {
        const val MAX_ITEMS = 200
        const val FORMAT = 1L
    }
}
