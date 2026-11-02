package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [InstanceRepository] over SQLite (AD-021). Multi-row changes run in one transaction so a reader never sees half of
 * a reorder, and deleting a factory deletes its cached pages with it (AD-022). It holds no secrets: the board key is in
 * the SecretStore, and the caller removes it through the SecretStore port.
 */
class SqlInstanceRepository internal constructor(
    private val db: PaperclipDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : InstanceRepository {
    private val queries get() = db.instanceConfigQueries

    override fun observeAll(): Flow<List<Instance>> =
        queries
            .selectAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.mapNotNull { it.toInstanceOrNull() } }

    override suspend fun get(id: InstanceId): Instance? =
        withContext(dispatcher) { queries.selectById(id.value).executeAsOneOrNull()?.toInstanceOrNull() }

    override suspend fun add(instance: Instance) {
        withContext(dispatcher) {
            with(instance) {
                queries.upsert(
                    id.value,
                    displayName,
                    baseUri.value,
                    deploymentMode.name,
                    exposure.name,
                    authMethod.name,
                    boardKeyId,
                    keyExpiresAt?.toString(),
                    defaultCompanyId?.value,
                    lastCompanyId?.value,
                    pins.toStoredPins(),
                    createdAt.toString(),
                    lastConnectedAt?.toString(),
                    sortOrder.toLong(),
                    status.name,
                )
            }
        }
    }

    override suspend fun update(instance: Instance) {
        withContext(dispatcher) {
            with(instance) {
                queries.updateExisting(
                    displayName,
                    baseUri.value,
                    deploymentMode.name,
                    exposure.name,
                    authMethod.name,
                    boardKeyId,
                    keyExpiresAt?.toString(),
                    defaultCompanyId?.value,
                    lastCompanyId?.value,
                    pins.toStoredPins(),
                    lastConnectedAt?.toString(),
                    status.name,
                    id.value,
                )
            }
        }
    }

    override suspend fun reorder(orderedIds: List<InstanceId>) {
        withContext(dispatcher) {
            db.transaction {
                orderedIds.forEachIndexed { position, id -> queries.setSortOrder(position.toLong(), id.value) }
            }
        }
    }

    override suspend fun delete(id: InstanceId) {
        withContext(dispatcher) {
            db.transaction {
                db.attentionCacheQueries.deleteForInstance(id.value)
                queries.deleteById(id.value)
            }
        }
    }
}
