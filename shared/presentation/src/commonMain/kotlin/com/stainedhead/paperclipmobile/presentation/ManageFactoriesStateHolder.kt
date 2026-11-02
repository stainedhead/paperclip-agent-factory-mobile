package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
import com.stainedhead.paperclipmobile.application.instance.RemoveInstance
import com.stainedhead.paperclipmobile.application.instance.RemoveInstanceResult
import com.stainedhead.paperclipmobile.application.instance.ReorderInstances
import com.stainedhead.paperclipmobile.application.instance.ReorderResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageRowUi(
    val id: String,
    val name: String,
    val address: String,
    val status: FactoryStatusUi,
)

/** What removing a factory deletes, stated before the person confirms (PRD FR-006). */
enum class RemovalEffect {
    Configuration,
    CachedData,
    StoredKey,
    ServerKey,
}

data class RemovalConfirmationUi(
    val id: String,
    val name: String,
    val effects: List<RemovalEffect>,
)

data class ManageFactoriesState(
    val rows: List<ManageRowUi> = emptyList(),
    val confirmation: RemovalConfirmationUi? = null,
    val isRemoving: Boolean = false,
    val deviceAuthIssue: DeviceAuthIssue? = null,
    val reorderRejected: Boolean = false,
    val lastRemovedKey: ServerKeyOutcome? = null,
)

sealed interface ManageFactoriesIntent {
    data class Reordered(
        val orderedIds: List<String>,
    ) : ManageFactoriesIntent

    data class RemoveRequested(
        val id: String,
    ) : ManageFactoriesIntent

    data object RemoveConfirmed : ManageFactoriesIntent

    data object RemoveCancelled : ManageFactoriesIntent

    data object MessageDismissed : ManageFactoriesIntent
}

data class ManageFactoriesUseCases(
    val observeInstances: ObserveInstances,
    val reorder: ReorderInstances,
    val remove: RemoveInstance,
)

/** State holder for Manage factories (screen 16): reorder and remove, with confirmation and device authentication. */
class ManageFactoriesStateHolder(
    private val useCases: ManageFactoriesUseCases,
    scope: CoroutineScope,
) {
    private val scope = childScope(scope)
    private val mutableState = MutableStateFlow(ManageFactoriesState())
    private val instances = MutableStateFlow<List<Instance>>(emptyList())
    val state: StateFlow<ManageFactoriesState> = mutableState.asStateFlow()

    init {
        this.scope.launch {
            useCases.observeInstances().collect { list ->
                instances.value = list
                mutableState.update {
                    it.copy(
                        rows =
                            list.map { i ->
                                ManageRowUi(i.id.value, i.displayName, i.baseUri.value, i.status.toUi())
                            },
                    )
                }
            }
        }
    }

    fun onIntent(intent: ManageFactoriesIntent) {
        when (intent) {
            is ManageFactoriesIntent.Reordered -> {
                reorder(intent.orderedIds)
            }

            is ManageFactoriesIntent.RemoveRequested -> {
                askToRemove(intent.id)
            }

            ManageFactoriesIntent.RemoveConfirmed -> {
                remove()
            }

            ManageFactoriesIntent.RemoveCancelled -> {
                mutableState.update { it.copy(confirmation = null) }
            }

            ManageFactoriesIntent.MessageDismissed -> {
                mutableState.update { it.copy(deviceAuthIssue = null, reorderRejected = false, lastRemovedKey = null) }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun reorder(orderedIds: List<String>) {
        scope.launch {
            val result = useCases.reorder(orderedIds.map { InstanceId(it) })
            mutableState.update { it.copy(reorderRejected = result == ReorderResult.Mismatch) }
        }
    }

    private fun askToRemove(id: String) {
        val instance = instances.value.firstOrNull { it.id.value == id } ?: return
        val effects =
            buildList {
                add(RemovalEffect.Configuration)
                add(RemovalEffect.CachedData)
                add(RemovalEffect.StoredKey)
                if (instance.status == InstanceStatus.SignedIn) add(RemovalEffect.ServerKey)
            }
        mutableState.update {
            it.copy(confirmation = RemovalConfirmationUi(id, instance.displayName, effects), deviceAuthIssue = null)
        }
    }

    private fun remove() {
        val current = mutableState.value
        val confirmation = current.confirmation ?: return
        if (current.isRemoving) return
        mutableState.update { it.copy(isRemoving = true, deviceAuthIssue = null) }
        scope.launch {
            val result = useCases.remove(InstanceId(confirmation.id))
            mutableState.update { it.afterRemoval(result) }
        }
    }

    private fun ManageFactoriesState.afterRemoval(result: RemoveInstanceResult): ManageFactoriesState =
        when (result) {
            is RemoveInstanceResult.Removed -> {
                copy(confirmation = null, isRemoving = false, lastRemovedKey = result.serverKey.toUi())
            }

            is RemoveInstanceResult.NotAuthorized -> {
                copy(isRemoving = false, deviceAuthIssue = result.result.toIssue())
            }

            RemoveInstanceResult.NotFound -> {
                copy(confirmation = null, isRemoving = false)
            }
        }
}
