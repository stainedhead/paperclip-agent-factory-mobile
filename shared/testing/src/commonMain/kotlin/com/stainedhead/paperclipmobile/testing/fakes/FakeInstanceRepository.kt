package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory configuration store. Counters let tests assert what was (not) written. */
class FakeInstanceRepository(
    initial: List<Instance> = emptyList(),
) : InstanceRepository {
    private val items = MutableStateFlow(initial)

    var addCalls = 0
        private set
    var updateCalls = 0
        private set
    var reorderCalls = 0
        private set

    /** Ids whose configuration and cache were deleted, in order. */
    val deletedIds = mutableListOf<InstanceId>()

    val current: List<Instance> get() = items.value.sortedBy { it.sortOrder }

    override fun observeAll(): Flow<List<Instance>> = items.map { list -> list.sortedBy { it.sortOrder } }

    override suspend fun get(id: InstanceId): Instance? = items.value.firstOrNull { it.id == id }

    override suspend fun add(instance: Instance) {
        addCalls += 1
        items.value = items.value.filterNot { it.id == instance.id } + instance
    }

    override suspend fun update(instance: Instance) {
        updateCalls += 1
        // Like the SQL adapter: the order and the creation time belong to add and reorder (review1b F-B-6).
        items.value =
            items.value.map {
                if (it.id == instance.id) instance.copy(sortOrder = it.sortOrder, createdAt = it.createdAt) else it
            }
    }

    override suspend fun reorder(orderedIds: List<InstanceId>) {
        reorderCalls += 1
        items.value =
            items.value.map { instance ->
                val position = orderedIds.indexOf(instance.id)
                if (position >= 0) instance.copy(sortOrder = position) else instance
            }
    }

    override suspend fun delete(id: InstanceId) {
        deletedIds += id
        items.value = items.value.filterNot { it.id == id }
    }
}
