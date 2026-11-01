package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.testing.TestData
import kotlinx.coroutines.flow.first

/** Behaviour every [InstanceRepository] must have. Real adapters subclass this in Phase 1b. */
abstract class InstanceRepositoryContract {
    abstract fun create(): InstanceRepository

    suspend fun runAll() {
        addedInstancesAreObservedInSortOrder()
        updateReplacesTheStoredInstance()
        reorderSetsSortOrderFromPosition()
        deleteRemovesOnlyThatInstance()
    }

    private suspend fun addedInstancesAreObservedInSortOrder() {
        val repo = create()
        repo.add(TestData.instance("b", "B", sortOrder = 1))
        repo.add(TestData.instance("a", "A", sortOrder = 0))
        check(repo.observeAll().first().map { it.id.value } == listOf("a", "b")) { "observeAll must sort by sortOrder" }
        check(repo.get(InstanceId("a"))?.displayName == "A") { "get must return the stored instance" }
        check(repo.get(InstanceId("zzz")) == null) { "get of an unknown id must be null" }
    }

    private suspend fun updateReplacesTheStoredInstance() {
        val repo = create()
        repo.add(TestData.instance("a", "A"))
        repo.update(TestData.instance("a", "Renamed"))
        check(repo.get(InstanceId("a"))?.displayName == "Renamed") { "update must replace the instance" }
        check(repo.observeAll().first().size == 1) { "update must not add a row" }
    }

    private suspend fun reorderSetsSortOrderFromPosition() {
        val repo = create()
        repo.add(TestData.instance("a", "A", sortOrder = 0))
        repo.add(TestData.instance("b", "B", sortOrder = 1))
        repo.reorder(listOf(InstanceId("b"), InstanceId("a")))
        check(repo.observeAll().first().map { it.id.value } == listOf("b", "a")) { "reorder must change the order" }
    }

    private suspend fun deleteRemovesOnlyThatInstance() {
        val repo = create()
        repo.add(TestData.instance("a", "A", sortOrder = 0))
        repo.add(TestData.instance("b", "B", sortOrder = 1))
        repo.delete(InstanceId("a"))
        check(repo.observeAll().first().map { it.id.value } == listOf("b")) { "delete must remove only that instance" }
        check(repo.get(InstanceId("a")) == null) { "a deleted instance must be gone" }
    }
}
