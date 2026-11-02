package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.securestore.KeychainSecretStore
import com.stainedhead.paperclipmobile.testing.contract.SecretStoreContract

/**
 * The device services built on iOS frameworks (Keychain). The iOS app test host uses this to run the Keychain tests,
 * which a bare Kotlin/Native test binary cannot do (it has no entitlements; research Q5a).
 */
object PlatformServices {
    /** A Keychain store whose items live under [service]. Tests pass a unique service so they touch no app items. */
    fun createKeychainSecretStore(service: String): SecretStore = KeychainSecretStore(service)

    /** Runs the shared [SecretStoreContract] on the real Keychain, each scenario in its own service. */
    suspend fun runKeychainContract() {
        val stores = mutableListOf<SecretStore>()
        val contract =
            object : SecretStoreContract() {
                override fun create(): SecretStore =
                    createKeychainSecretStore("pafm.contract.${stores.size}.${uniqueId()}").also {
                        stores +=
                            it
                    }
            }
        try {
            contract.runAll()
        } finally {
            stores.forEach { store -> listOf("a", "b").forEach { store.deleteNamespace(InstanceId(it)) } }
        }
    }

    private fun uniqueId(): String = platform.Foundation.NSUUID().UUIDString
}
