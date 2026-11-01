package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.contract.AppSettingsStoreContract
import com.stainedhead.paperclipmobile.testing.contract.AttentionCacheContract
import com.stainedhead.paperclipmobile.testing.contract.InstanceRepositoryContract
import com.stainedhead.paperclipmobile.testing.contract.SecretStoreContract
import com.stainedhead.paperclipmobile.testing.fakes.FakeAppSettingsStore
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionCache
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FakeInstanceRepositoryContractTest : InstanceRepositoryContract() {
    override fun create(): InstanceRepository = FakeInstanceRepository()

    @Test
    fun `FR-002 the fake passes the instance repository contract for non secret configuration`() = runTest { runAll() }
}

class FakeSecretStoreContractTest : SecretStoreContract() {
    override fun create(): SecretStore = FakeSecretStore()

    @Test
    fun `FR-013 the fake passes the secret store contract for the board token per instance`() = runTest { runAll() }
}

class FakeAppSettingsStoreContractTest : AppSettingsStoreContract() {
    override fun create(): AppSettingsStore = FakeAppSettingsStore(AppSettings("Initial"))

    @Test
    fun `the fake passes the app settings store contract`() = runTest { runAll() }
}

class FakeAttentionCacheContractTest : AttentionCacheContract() {
    override fun create(): AttentionCache = FakeAttentionCache()

    @Test
    fun `the fake passes the attention cache contract`() = runTest { runAll() }
}
