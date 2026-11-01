package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult

/**
 * Checks and normalises an instance address typed, pasted or scanned by the user (PRD FR-003).
 *
 * @param allowInsecureLocal true only in debug builds, where `http` is accepted for `localhost` and `10.0.2.2`.
 */
class ValidateInstanceAddress(
    private val allowInsecureLocal: Boolean,
) {
    operator fun invoke(input: String): BaseUriResult = BaseUri.parse(input, allowInsecureLocal)
}
