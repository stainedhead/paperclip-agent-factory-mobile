package com.stainedhead.paperclipmobile.application.attention

import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** What [LoadInbox] reports, in order (ADR-017). */
sealed interface InboxLoad {
    /** The last feed this phone saw. Shown at once, labelled with its own `generatedAt`. */
    data class Cached(
        val feed: AttentionFeed,
    ) : InboxLoad

    data class Fresh(
        val feed: AttentionFeed,
    ) : InboxLoad

    /** The refresh failed. Whatever was [Cached] stays on screen. */
    data class Failed(
        val error: DomainError,
    ) : InboxLoad
}

/** Reads the first page of the feed, retrying reads (PRD FR-096), and replaces the cached copy on success. */
class RefreshInbox(
    private val gateway: AttentionGateway,
    private val cache: AttentionCache,
) {
    suspend operator fun invoke(scope: CompanyScope): DomainResult<AttentionFeed> {
        val result = retryingRead { gateway.feed(scope, FeedQuery()) }
        if (result is DomainResult.Success) cache.write(scope, result.value)
        return result
    }
}

/** Cache first, then the network (PRD FR-095). */
class LoadInbox(
    private val cache: AttentionCache,
    private val refresh: RefreshInbox,
) {
    operator fun invoke(scope: CompanyScope): Flow<InboxLoad> =
        flow {
            cache.read(scope)?.let { emit(InboxLoad.Cached(it)) }
            when (val result = refresh(scope)) {
                is DomainResult.Success -> emit(InboxLoad.Fresh(result.value))
                is DomainResult.Failure -> emit(InboxLoad.Failed(result.error))
            }
        }
}

/** The next page. Pages after the first are never cached, so a stale cursor can never be replayed. */
class LoadMoreInbox(
    private val gateway: AttentionGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        cursor: String,
    ): DomainResult<AttentionFeed> = retryingRead { gateway.feed(scope, FeedQuery(cursor = cursor)) }
}

/** The cached first page only, for when the phone is offline (PRD FR-095). */
class ReadCachedInbox(
    private val cache: AttentionCache,
) {
    suspend operator fun invoke(scope: CompanyScope): AttentionFeed? = cache.read(scope)
}
