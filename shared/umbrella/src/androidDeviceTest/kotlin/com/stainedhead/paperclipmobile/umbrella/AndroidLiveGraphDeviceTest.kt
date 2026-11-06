package com.stainedhead.paperclipmobile.umbrella

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.persistence.PaperclipPersistence
import com.stainedhead.paperclipmobile.infrastructure.persistence.openAndroidPersistence
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val TEST_ALIAS_PREFIX = "pafm.test.livegraph/"

/**
 * AND-004 on the emulator: the real Android live graph, end to end. The Keystore, the database in `noBackupFilesDir`
 * and the system network monitor are the real ones; no server is needed to reach the empty first-run state.
 */
@RunWith(AndroidJUnit4::class)
class AndroidLiveGraphDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val settings =
        AndroidLiveSettings("Unlock", "Confirm", "Protect the board key", keyAliasPrefix = TEST_ALIAS_PREFIX)
    private val opened = mutableListOf<PaperclipGraph>()

    @After
    fun cleanUp() {
        opened.forEach { it.close() }
        listOf("", "-wal", "-shm", "-journal").forEach { File(context.noBackupFilesDir, "paperclip.db$it").delete() }
        File(context.noBackupFilesDir, "secrets").deleteRecursively()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore
            .aliases()
            .toList()
            .filter { it.startsWith(TEST_ALIAS_PREFIX) }
            .forEach { keyStore.deleteEntry(it) }
    }

    private fun open(allowInsecureLocal: Boolean = false): PaperclipGraph {
        val result = AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, FakePairingBrowser(), settings)
        return assertIs<AndroidGraphOpen.Opened>(result).graph.also { opened += it }
    }

    @Test
    fun theRealGraphOpensAtTheEmptyFirstRunState() =
        runBlocking {
            val graph = open()

            val firstRun = graph.createFirstRunStateHolder()
            assertEquals(FirstRunStep.Address, firstRun.state.value.step)
            val settingsHolder = graph.createSettingsStateHolder()
            val factories = graph.createFactoriesStateHolder()
            val loaded = withTimeout(20.seconds) { settingsHolder.state.first { it.deviceName.isNotEmpty() } }
            assertEquals(emptyList(), loaded.factories)
            assertTrue(factories.state.value.isEmpty)
            // The holders go first: a holder still reading the database when it is closed would crash the process.
            firstRun.close()
            settingsHolder.close()
            factories.close()
        }

    @Test
    fun theDevicePortsAreTheRealAdaptersAndNoneIsAFake() {
        val graph = open()

        val bound =
            listOf(
                graph.resolveRaw<SecretStore>(),
                graph.resolve<Biometrics>(),
                graph.resolve<Connectivity>(),
                graph.resolve<Clock>(),
                graph.resolve<InstanceRepository>(),
                graph.resolve<AttentionGateway>(),
            ).map { it::class.qualifiedName.orEmpty() }

        assertEquals(emptyList(), bound.filter { it.contains(".testing.") || it.contains("Fake") })
        assertTrue(bound[0].endsWith(".KeystoreSecretStore"), bound[0])
        assertTrue(bound[1].endsWith(".BiometricPromptBiometrics"), bound[1])
        assertTrue(bound[2].endsWith(".ConnectivityManagerConnectivity"), bound[2])
        // What the app receives as its SecretStore is the session cache over that device store (ADR-145).
        assertTrue(
            graph.resolve<SecretStore>()::class.qualifiedName.orEmpty().endsWith(".SessionCachingSecretStore"),
        )
    }

    @Test
    fun aBoardKeyIsSealedUnderTheConfiguredAliasPrefix() =
        runBlocking {
            val graph = open()
            val store = graph.resolve<SecretStore>()
            val key = SecretKey(InstanceId("a"), SecretName.BoardToken)

            store.put(key, Secret.of(TestData.TOKEN), SecretPolicy(requireUserPresence = false))

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertTrue(keyStore.aliases().toList().any { it.startsWith(TEST_ALIAS_PREFIX) })
            store.deleteNamespace(InstanceId("a"))
        }

    @Test
    fun releaseUnregistersTheNetworkMonitor() {
        var closed = 0
        val monitor =
            object : Connectivity by FakeConnectivity(), AutoCloseable {
                override fun close() {
                    closed++
                }
            }
        val platform =
            AndroidLivePlatform(
                secretStore = FakeSecretStore(),
                biometrics = FakeBiometrics(),
                openPersistence = { openAndroidPersistence(context, it) },
                openConnectivity = { monitor },
            )

        platform.release()
        platform.persistence.close()

        assertEquals(1, closed)
    }

    @Test
    fun aNetworkMonitorThatCannotBeCreatedClosesTheDatabaseAlreadyOpened() {
        var database: PaperclipPersistence? = null

        val failure =
            runCatching {
                AndroidLivePlatform(
                    secretStore = FakeSecretStore(),
                    biometrics = FakeBiometrics(),
                    openPersistence = { clock -> openAndroidPersistence(context, clock).also { database = it } },
                    openConnectivity = { error("the system refused the network monitor") },
                )
            }.exceptionOrNull()

        assertIs<IllegalStateException>(failure)
        val closed = database ?: error("the database was never opened")
        assertTrue(runCatching { runBlocking { closed.instances.get(InstanceId("any")) } }.isFailure)
    }

    @Test
    fun aReleaseGraphRefusesAStoredHttpAddressBeforeAnyRequest() =
        runBlocking {
            val localUri =
                (
                    BaseUri.parse(
                        "http://localhost:3100",
                        allowInsecureLocal = true,
                    ) as BaseUriResult.Valid
                ).uri
            val local = TestData.instance("local-1").copy(baseUri = localUri)
            val seed = androidLivePlatform(context, settings, AndroidAuthHost)
            seed.persistence.instances.add(local)
            seed.secretStore.put(
                SecretKey(local.id, SecretName.BoardToken),
                Secret.of(TestData.TOKEN),
                SecretPolicy(requireUserPresence = false),
            )
            seed.persistence.close()
            seed.release()

            val graph = open(allowInsecureLocal = false)
            val refused =
                graph.resolve<AttentionGateway>().feed(
                    CompanyScope(local.id, CompanyId("company-1")),
                    FeedQuery(),
                )

            assertEquals(DomainResult.Failure(DomainError.CertificateInvalid), refused)
            graph.resolve<SecretStore>().deleteNamespace(local.id)
        }
}
