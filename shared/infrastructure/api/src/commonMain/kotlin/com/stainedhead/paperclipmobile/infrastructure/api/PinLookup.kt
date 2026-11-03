package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import kotlinx.coroutines.flow.first

/**
 * The certificate pins of the factory configured at an address, for calls that are made before a key exists
 * (health probe, pairing, pending-key checks) and therefore have no instance id (review1b F-A-1, ADR-075). A factory
 * that is not configured yet has no pins; every factory at the address contributes its pins, so a re-pair or a
 * probe of a pinned factory fails closed exactly like its stored-key calls.
 */
internal class PinLookup(
    private val instances: InstanceRepository,
) {
    suspend operator fun invoke(baseUri: BaseUri): List<SpkiPin> =
        instances
            .observeAll()
            .first()
            .filter { it.baseUri == baseUri }
            .flatMap { it.pins }
            .distinct()
}
