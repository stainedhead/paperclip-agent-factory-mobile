package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.contract.SecretStoreContract
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The caching decorator is a [SecretStore] and passes the shared contract. [createProtected] stays `null` on purpose:
 * the contract's protected cases say "every read prompts, an approval is never reused", which is true of the device
 * stores (ADR-139, still proven on `KeystoreSecretStore`) and deliberately not of this decorator (ADR-145). Its own
 * prompt counts are in [SessionCachingSecretStoreTest].
 */
class SessionCachingSecretStoreContractTest : SecretStoreContract() {
    override fun create(): SecretStore = SessionCachingSecretStore(FakeSecretStore())

    @Test
    fun `FR-013 the session cache passes the secret store contract for the board token per instance`() =
        runTest { runAll() }
}
