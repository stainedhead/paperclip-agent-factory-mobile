package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.auth.CheckCredentials
import com.stainedhead.paperclipmobile.application.auth.CredentialState
import com.stainedhead.paperclipmobile.application.auth.KeyExpiryStatus
import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
import com.stainedhead.paperclipmobile.application.instance.SwitchCompany
import com.stainedhead.paperclipmobile.application.instance.SwitchInstance
import com.stainedhead.paperclipmobile.application.settings.ObserveAppSettings
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FactoryRowUi(
    val id: String,
    val name: String,
    val address: String,
    val status: FactoryStatusUi,
    val expiry: ExpiryUi,
    val companies: List<CompanyUi>,
    val selectedCompanyId: String?,
    val isSelected: Boolean,
    val isLoading: Boolean,
    val error: ErrorUi?,
)

/** [updatedAtEpochMillis] is when the companies were last read from the server (PRD FR-095). */
data class FactoriesState(
    val rows: List<FactoryRowUi> = emptyList(),
    val selectedInstanceId: String? = null,
    val isEmpty: Boolean = true,
    val isOffline: Boolean = false,
    val canRefresh: Boolean = true,
    val isRefreshing: Boolean = false,
    val updatedAtEpochMillis: Long? = null,
)

sealed interface FactoriesIntent {
    data object Refresh : FactoriesIntent

    data class InstanceSelected(
        val instanceId: String,
    ) : FactoriesIntent

    data class CompanySelected(
        val instanceId: String,
        val companyId: String,
    ) : FactoriesIntent
}

data class FactoriesUseCases(
    val observeInstances: ObserveInstances,
    val observeSettings: ObserveAppSettings,
    val checkCredentials: CheckCredentials,
    val loadAccess: LoadInstanceAccess,
    val switchInstance: SwitchInstance,
    val switchCompany: SwitchCompany,
    val expiry: KeyExpiryStatus,
)

private data class Loaded(
    val companies: List<CompanyUi> = emptyList(),
    val error: ErrorUi? = null,
    val isLoading: Boolean = false,
)

private data class Progress(
    val loaded: Map<String, Loaded> = emptyMap(),
    val isRefreshing: Boolean = false,
    val updatedAt: Long? = null,
)

/** State holder for the Factories tab (screen 8): every factory, its companies and roles, and the current selection. */
class FactoriesStateHolder(
    private val useCases: FactoriesUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    private val scope = childScope(scope)
    private val progress = MutableStateFlow(Progress())
    private val mutableState = MutableStateFlow(FactoriesState())
    private var instances: List<Instance> = emptyList()
    val state: StateFlow<FactoriesState> = mutableState.asStateFlow()

    init {
        this.scope.launch {
            combine(
                useCases.observeInstances(),
                useCases.observeSettings(),
                connectivity.isOnline,
                progress,
            ) { list, settings, online, progress ->
                instances = list
                render(list, settings, online, progress)
            }.collect { mutableState.value = it }
        }
    }

    fun onIntent(intent: FactoriesIntent) {
        when (intent) {
            FactoriesIntent.Refresh -> {
                refresh()
            }

            is FactoriesIntent.InstanceSelected -> {
                scope.launch { useCases.switchInstance(InstanceId(intent.instanceId)) }
            }

            is FactoriesIntent.CompanySelected -> {
                scope.launch {
                    useCases.switchCompany(InstanceId(intent.instanceId), CompanyId(intent.companyId))
                    useCases.switchInstance(InstanceId(intent.instanceId))
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun refresh() {
        if (!connectivity.isOnline.value || progress.value.isRefreshing) return
        progress.update { it.copy(isRefreshing = true) }
        scope.launch {
            var anySuccess = false
            instances.forEach { anySuccess = load(it) || anySuccess }
            progress.update {
                it.copy(
                    isRefreshing = false,
                    updatedAt = if (anySuccess) clock.now().toEpochMilliseconds() else it.updatedAt,
                )
            }
        }
    }

    private suspend fun load(instance: Instance): Boolean {
        if (instance.status != InstanceStatus.SignedIn) return false
        if (useCases.checkCredentials(instance.id) != CredentialState.Usable) return false
        setLoaded(instance.id) { it.copy(isLoading = true, error = null) }
        return when (val result = useCases.loadAccess(instance.id)) {
            is DomainResult.Success -> {
                setLoaded(instance.id) { Loaded(result.value.companies.map { c -> c.toUi() }) }
                true
            }

            is DomainResult.Failure -> {
                setLoaded(instance.id) { it.copy(isLoading = false, error = result.error.toErrorUi()) }
                false
            }
        }
    }

    private fun setLoaded(
        id: InstanceId,
        change: (Loaded) -> Loaded,
    ) = progress.update { it.copy(loaded = it.loaded + (id.value to change(it.loaded[id.value] ?: Loaded()))) }

    private fun render(
        list: List<Instance>,
        settings: AppSettings,
        online: Boolean,
        progress: Progress,
    ): FactoriesState {
        val selected = settings.selectedInstanceId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        return FactoriesState(
            rows = list.map { row(it, progress.loaded[it.id.value] ?: Loaded(), it.id == selected) },
            selectedInstanceId = selected?.value,
            isEmpty = list.isEmpty(),
            isOffline = !online,
            canRefresh = online && !progress.isRefreshing && list.isNotEmpty(),
            isRefreshing = progress.isRefreshing,
            updatedAtEpochMillis = progress.updatedAt,
        )
    }

    private fun row(
        instance: Instance,
        loaded: Loaded,
        isSelected: Boolean,
    ) = FactoryRowUi(
        id = instance.id.value,
        name = instance.displayName,
        address = instance.baseUri.value,
        status = instance.status.toUi(),
        expiry = useCases.expiry(instance.keyExpiresAt).toUi(),
        companies = loaded.companies,
        selectedCompanyId = instance.activeCompanyId?.value,
        isSelected = isSelected,
        isLoading = loaded.isLoading,
        error = loaded.error,
    )
}
