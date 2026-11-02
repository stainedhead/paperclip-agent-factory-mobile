package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.attention.InboxLoad
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Inbox's loading, paging and mutation plumbing, shared by the holder and its action helpers. */
internal class InboxSession(
    val useCases: InboxUseCases,
    val connectivity: Connectivity,
    val scope: CoroutineScope,
    val companyScope: CompanyScope,
    val model: MutableStateFlow<InboxModel>,
) {
    suspend fun ensureContext(): CompanyContext? {
        model.value.context?.let { return it }
        return when (val result = useCases.resolveContext(companyScope)) {
            is DomainResult.Success -> {
                model.update { it.copy(context = result.value) }
                result.value
            }

            is DomainResult.Failure -> {
                model.update { it.copy(error = result.error.toErrorUi(), refreshing = false) }
                null
            }
        }
    }

    suspend fun start() {
        val instance = useCases.getInstance(companyScope.instanceId)
        model.update { it.copy(baseUrl = instance?.baseUri?.value.orEmpty()) }
        if (connectivity.isOnline.value) loadFresh() else showCache()
    }

    private suspend fun showCache() {
        useCases.readCache(companyScope)?.let { feed -> model.update { it.withFirstPage(feed, fromCache = true) } }
    }

    private suspend fun loadFresh() {
        model.update { it.copy(refreshing = true) }
        if (ensureContext() == null) return
        useCases.load(companyScope).collect { load ->
            model.update {
                when (load) {
                    is InboxLoad.Cached -> it.withFirstPage(load.feed, fromCache = true)
                    is InboxLoad.Fresh -> it.withFirstPage(load.feed, fromCache = false)
                    is InboxLoad.Failed -> it.copy(error = load.error.toErrorUi())
                }
            }
        }
        model.update { it.copy(refreshing = false) }
    }

    suspend fun refresh() {
        if (!connectivity.isOnline.value || !model.claim({ it.refreshing }) { it.copy(refreshing = true) }) return
        if (ensureContext() == null) return
        when (val result = useCases.refresh(companyScope)) {
            is DomainResult.Success -> {
                model.update {
                    it
                        .withFirstPage(
                            result.value,
                            fromCache = false,
                        ).copy(refreshing = false)
                }
            }

            is DomainResult.Failure -> {
                model.update { it.copy(error = result.error.toErrorUi(), refreshing = false) }
            }
        }
    }

    suspend fun loadMore() {
        val cursor = model.value.nextCursor
        if (cursor == null || !connectivity.isOnline.value) return
        if (!model.claim({ it.loadingMore }) { it.copy(loadingMore = true) }) return
        when (val result = useCases.loadMore(companyScope, cursor)) {
            is DomainResult.Success -> {
                model.update { it.withNextPage(result.value).copy(loadingMore = false) }
            }

            is DomainResult.Failure -> {
                model.update { it.copy(loadingMore = false) }
                // The server no longer knows the cursor: start again from the first page.
                if (result.error is DomainError.Rejected) {
                    refresh()
                } else {
                    model.update {
                        it.copy(
                            error = result.error.toErrorUi(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Runs one action. A second tap while one is in flight, or any tap while offline, is ignored. Whatever the outcome
     * says may have changed on the server (done, reconciled, or a timeout) triggers a re-read of the feed.
     */
    internal fun <T> mutate(
        action: suspend (CompanyContext) -> MutationResult<T>,
        onResult: (MutationResult<T>) -> Unit = {},
    ) {
        if (!connectivity.isOnline.value) return
        if (!model.claim({ it.mutating }) { it.copy(mutating = true, feedback = null) }) return
        scope.launch {
            val context = ensureContext()
            if (context == null) {
                model.update { it.copy(mutating = false) }
                return@launch
            }
            val result = action(context)
            model.update { it.copy(mutating = false, feedback = result.toFeedback()) }
            onResult(result)
            if (result.mayHaveChangedTheServer()) refresh()
        }
    }

    private fun MutationResult<*>.mayHaveChangedTheServer() =
        this is MutationResult.Done ||
            this is MutationResult.Reconciled ||
            this is MutationResult.Unknown ||
            (this is MutationResult.Failed && error == DomainError.Timeout)
}
