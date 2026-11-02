package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
import com.stainedhead.paperclipmobile.application.instance.SwitchCompany
import com.stainedhead.paperclipmobile.application.instance.SwitchInstance
import com.stainedhead.paperclipmobile.application.settings.ObserveAppSettings
import com.stainedhead.paperclipmobile.application.settings.ShouldLockApp
import com.stainedhead.paperclipmobile.application.settings.UnlockApp
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

enum class LockState {
    Locked,
    Unlocked,
}

/** What the app shell needs: the lock, whether to show setup or the tabs, and the current factory and company. */
data class AppState(
    val isLoaded: Boolean = false,
    val lock: LockState = LockState.Locked,
    val unlockIssue: DeviceAuthIssue? = null,
    val hasFactories: Boolean = false,
    val selectedInstanceId: String? = null,
    val selectedInstanceName: String? = null,
    val selectedCompanyId: String? = null,
)

sealed interface AppIntent {
    data object WentToBackground : AppIntent

    data object CameToForeground : AppIntent

    data object UnlockRequested : AppIntent

    data class InstanceSelected(
        val instanceId: String,
    ) : AppIntent

    data class CompanySelected(
        val instanceId: String,
        val companyId: String,
    ) : AppIntent
}

data class AppUseCases(
    val observeSettings: ObserveAppSettings,
    val observeInstances: ObserveInstances,
    val unlock: UnlockApp,
    val shouldLock: ShouldLockApp,
    val switchInstance: SwitchInstance,
    val switchCompany: SwitchCompany,
)

/**
 * State holder for the app shell: the app lock (PRD FR-014) and the selected factory and company (PRD FR-007).
 * It starts locked, so nothing shows before the first settings read.
 */
class AppStateHolder(
    private val useCases: AppUseCases,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    private val scope = childScope(scope)
    private val mutableState = MutableStateFlow(AppState())

    /** Written by the settings collector and by intents on other threads, so it lives in one atomic cell (F-B-6). */
    private val internals = MutableStateFlow(Internals())
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    init {
        this.scope.launch {
            combine(useCases.observeSettings(), useCases.observeInstances()) { s, list ->
                val first = internals.getAndUpdate { it.copy(settings = s) }.settings == null
                mutableState.update { it.fromSources(s, list, first) }
            }.collect {}
        }
    }

    fun onIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.WentToBackground -> {
                val now = clock.now()
                internals.update { it.copy(backgroundedAt = now) }
            }

            AppIntent.CameToForeground -> {
                relock()
            }

            AppIntent.UnlockRequested -> {
                unlock()
            }

            is AppIntent.InstanceSelected -> {
                scope.launch { useCases.switchInstance(InstanceId(intent.instanceId)) }
            }

            is AppIntent.CompanySelected -> {
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

    private fun AppState.fromSources(
        s: AppSettings,
        list: List<Instance>,
        firstRead: Boolean,
    ): AppState {
        val selected = s.selectedInstanceId?.let { id -> list.firstOrNull { it.id == id } } ?: list.firstOrNull()
        val lock =
            when {
                !s.appLockEnabled -> LockState.Unlocked
                firstRead -> if (useCases.shouldLock(s, null)) LockState.Locked else LockState.Unlocked
                else -> lock
            }
        return copy(
            isLoaded = true,
            lock = lock,
            hasFactories = list.isNotEmpty(),
            selectedInstanceId = selected?.id?.value,
            selectedInstanceName = selected?.displayName,
            selectedCompanyId = selected?.activeCompanyId?.value,
        )
    }

    private fun relock() {
        val current = internals.value.settings ?: return
        val since = internals.getAndUpdate { it.copy(backgroundedAt = null) }.backgroundedAt ?: return
        if (useCases.shouldLock(
                current,
                since,
            )
        ) {
            mutableState.update { it.copy(lock = LockState.Locked, unlockIssue = null) }
        }
    }

    private fun unlock() {
        if (mutableState.value.lock == LockState.Unlocked) return
        if (!internals.claim({ it.unlocking }) { it.copy(unlocking = true) }) return
        scope.launch {
            val result =
                try {
                    useCases.unlock()
                } finally {
                    internals.update { it.copy(unlocking = false) }
                }
            mutableState.update {
                if (result == BiometricResult.Approved) {
                    it.copy(lock = LockState.Unlocked, unlockIssue = null)
                } else {
                    it.copy(unlockIssue = result.toIssue())
                }
            }
        }
    }
}

private data class Internals(
    val settings: AppSettings? = null,
    val backgroundedAt: Instant? = null,
    val unlocking: Boolean = false,
)
