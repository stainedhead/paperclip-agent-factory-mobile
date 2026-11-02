package com.stainedhead.paperclipmobile.infrastructure.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorDomain
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import kotlin.coroutines.resume

/** The system prompt through `LAContext`. A new context is made per prompt, as a context caches an approval. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class LAContextAuthenticator : DeviceAuthenticator {
    override suspend fun evaluate(reasonText: String): AuthOutcome =
        suspendCancellableCoroutine { continuation ->
            val context = LAContext()
            val unavailable = unavailableOutcome(context)
            if (unavailable != null) {
                continuation.resume(unavailable)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reasonText) { success, error ->
                continuation.resume(if (success) AuthOutcome.Success else error.toOutcome())
            }
        }

    /** The failure when the policy cannot be evaluated at all (no passcode, no biometrics), else null. */
    private fun unavailableOutcome(context: LAContext): AuthOutcome? =
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (context.canEvaluatePolicy(
                    LAPolicyDeviceOwnerAuthentication,
                    error.ptr,
                )
            ) {
                null
            } else {
                error.value.toOutcome()
            }
        }

    private fun NSError?.toOutcome(): AuthOutcome =
        when {
            this == null -> AuthOutcome.Failure(LAErrorAuthenticationFailed)
            domain != LAErrorDomain -> AuthOutcome.ServiceUnreachable
            else -> AuthOutcome.Failure(code)
        }
}
