package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.port.AttentionCache

/** An in-memory [AttentionCache] that cuts a long feed like the real one. Counts writes for assertions. */
class FakeAttentionCache : AttentionCache {
    private val feeds = mutableMapOf<CompanyScope, AttentionFeed>()
    var writes = 0
        private set

    override suspend fun read(scope: CompanyScope): AttentionFeed? = feeds[scope]

    override suspend fun write(
        scope: CompanyScope,
        feed: AttentionFeed,
    ) {
        writes += 1
        feeds[scope] =
            if (feed.items.size <= AttentionCache.MAX_ITEMS) {
                feed
            } else {
                feed.copy(items = feed.items.take(AttentionCache.MAX_ITEMS), nextCursor = null)
            }
    }

    override suspend fun clear(scope: CompanyScope) {
        feeds.remove(scope)
    }
}
