package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository

/** Reads one configured factory. */
class GetInstance(
    private val repository: InstanceRepository,
) {
    suspend operator fun invoke(id: InstanceId): Instance? = repository.get(id)
}
