package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Every configured factory in the order the person arranged them. */
class ObserveInstances(
    private val repository: InstanceRepository,
) {
    operator fun invoke(): Flow<List<Instance>> = repository.observeAll().map { list -> list.sortedBy { it.sortOrder } }
}

enum class ReorderResult {
    Reordered,
    Mismatch,
}

/** Saves a new factory order. The list must contain exactly the configured factories (PRD FR-006). */
class ReorderInstances(
    private val repository: InstanceRepository,
) {
    suspend operator fun invoke(orderedIds: List<InstanceId>): ReorderResult {
        val known = repository.observeAll().first().map { it.id }
        if (orderedIds.size != known.size || orderedIds.toSet() != known.toSet()) return ReorderResult.Mismatch
        repository.reorder(orderedIds)
        return ReorderResult.Reordered
    }
}

enum class SwitchResult {
    Switched,
    NotFound,
}

/** Makes a factory the selected one; the choice is restored on launch (PRD FR-007). */
class SwitchInstance(
    private val repository: InstanceRepository,
    private val settings: AppSettingsStore,
) {
    suspend operator fun invoke(id: InstanceId): SwitchResult {
        if (repository.get(id) == null) return SwitchResult.NotFound
        settings.save(settings.observe().first().copy(selectedInstanceId = id))
        return SwitchResult.Switched
    }
}

/** Remembers which company was last open in a factory (PRD FR-007). */
class SwitchCompany(
    private val repository: InstanceRepository,
    private val locks: InstanceLocks,
) {
    suspend operator fun invoke(
        instanceId: InstanceId,
        companyId: CompanyId,
    ): SwitchResult {
        val stored = locks.modify(repository, instanceId) { it.copy(lastCompanyId = companyId) }
        return if (stored == null) SwitchResult.NotFound else SwitchResult.Switched
    }
}
