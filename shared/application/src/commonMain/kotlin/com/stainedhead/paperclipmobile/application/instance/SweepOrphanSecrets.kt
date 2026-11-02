package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Deletes the secrets of every factory that has no configuration row. The device secret store outlives the app's
 * database (an iOS Keychain item survives even a reinstall), so when the database had to be recreated empty the board
 * keys of the lost factories would stay on the phone with nothing to use or remove them (review1b F-B-2, ADR-078).
 * Returns how many namespaces were removed; one that cannot be deleted is left for the next sweep and not counted.
 */
class SweepOrphanSecrets(
    private val repository: InstanceRepository,
    private val secrets: SecretStore,
) {
    suspend operator fun invoke(): Int {
        val known =
            repository
                .observeAll()
                .first()
                .map { it.id }
                .toSet()
        var removed = 0
        for (orphan in secrets.namespaces() - known) {
            try {
                secrets.deleteNamespace(orphan)
                removed += 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                // A secure store that refuses one namespace must not keep the others on the phone.
            }
        }
        return removed
    }
}
