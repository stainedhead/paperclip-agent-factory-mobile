package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The persistence module (`shared/infrastructure/persistence`, Phase 1b AD-020 to AD-022): the SQLDelight classes it
 * generates stay inside it, the only public things are the three SQL adapters, their holder and the platform factories,
 * and no secret type is ever imported.
 */
class InfrastructurePersistenceRulesTest {
    private val persistenceRoot = File(projectRoot, "shared/infrastructure/persistence")

    private fun surfaceOffenders(root: File): List<String> =
        mainSources(root)
            .flatMap { file -> publicTopLevel(file.readText()).map { "${file.name}: $it" } }
            .filterNot { it.substringAfter(": ").let { name -> name.startsWith("Sql") || name in ALLOWED_PUBLIC } }

    @Test
    fun `the persistence module exposes only the SQL adapters, their holder and the factories`() {
        val sources = mainSources(persistenceRoot)
        Assertions.assertTrue(sources.isNotEmpty(), "No persistence sources found: the rule would pass vacuously")
        val offenders = surfaceOffenders(persistenceRoot)
        Assertions.assertTrue(offenders.isEmpty(), "Public declarations outside the persistence surface: $offenders")
    }

    @Test
    fun `public classes build only from internal constructors so generated types never show in a signature`() {
        val offenders =
            mainSources(persistenceRoot).flatMap { file ->
                classesWithoutInternalConstructor(file.readText(), PUBLIC_CLASSES).map { "${file.name}: $it" }
            }
        Assertions.assertTrue(offenders.isEmpty(), "Public classes need `internal constructor`: $offenders")
    }

    @Test
    fun `no file outside the persistence module names the generated database package`() {
        val outside =
            File(projectRoot, "shared")
                .walkTopDown()
                .onEnter { it.name != "build" && it != persistenceRoot }
                .filter { it.extension == "kt" }
                .filter {
                    "$PERSISTENCE_PACKAGE.db" in it.readText() || "$PERSISTENCE_PACKAGE.payload" in it.readText()
                }.map { it.relativeTo(projectRoot).invariantSeparatorsPath }
                .toList()
        Assertions.assertTrue(outside.isEmpty(), "Generated and payload types must stay in persistence: $outside")
    }

    @Test
    fun `the persistence module imports only domain, SQLDelight, the standard library and the platform`() {
        val offenders =
            mainSources(persistenceRoot).flatMap { file ->
                disallowedImports(file.readText(), ALLOWED_IMPORTS).map { "${file.name}: $it" }
            }
        Assertions.assertTrue(offenders.isEmpty(), "Unexpected imports in infrastructure/persistence: $offenders")
    }

    @Test
    fun `no persistence source imports a secret type`() {
        val offenders =
            mainSources(persistenceRoot).flatMap { file ->
                disallowedImports(file.readText(), emptyList())
                    .filter { it.startsWith("$APP_PACKAGE.domain.Secret") }
                    .map { "${file.name}: $it" }
            }
        Assertions.assertTrue(offenders.isEmpty(), "Persistence must never see a Secret or SecretKey: $offenders")
    }

    @Test
    fun `serializable payload types live in the payload package and are internal`() {
        val sources = mainSources(persistenceRoot)
        val outside =
            sources.filter { "/payload/" !in it.invariantSeparatorsPath }.filter { "@Serializable" in it.readText() }
        Assertions.assertTrue(outside.isEmpty(), "@Serializable types belong in the payload package: $outside")
        val payload = sources.filter { "/payload/" in it.invariantSeparatorsPath }
        Assertions.assertTrue(payload.isNotEmpty(), "No payload sources found: the rule would pass vacuously")
        val publicOnes = payload.flatMap { file -> publicTopLevel(file.readText()).map { "${file.name}: $it" } }
        Assertions.assertTrue(publicOnes.isEmpty(), "Payload declarations must be internal: $publicOnes")
    }

    @Test
    fun `the surface rule fails on a planted suspend fun, const val and public class`() {
        val root =
            kotlin.io.path
                .createTempDirectory("pafm-persistence-surface")
                .toFile()
        try {
            val dir = File(root, "src/commonMain/kotlin").also { it.mkdirs() }
            File(dir, "Planted.kt").writeText(
                "suspend fun leakDb() {}\nconst val KEY = \"x\"\npublic class Loud\nclass SqlFine\n",
            )
            Assertions.assertEquals(
                listOf("Planted.kt: leakDb", "Planted.kt: KEY", "Planted.kt: Loud"),
                surfaceOffenders(root),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the checks see a public declaration, a stray import and a secret import`() {
        Assertions.assertEquals(
            listOf("Leak", "createThing"),
            publicTopLevel("class Leak\ninternal class Fine\nfun createThing() {}\n"),
        )
        val source = "import $APP_PACKAGE.domain.Secret\nimport $APP_PACKAGE.application.SignOut\n"
        Assertions.assertEquals(
            listOf("$APP_PACKAGE.application.SignOut"),
            disallowedImports(source, ALLOWED_IMPORTS),
        )
        Assertions.assertEquals(
            listOf("$APP_PACKAGE.domain.Secret"),
            disallowedImports(source, emptyList()).filter { it.startsWith("$APP_PACKAGE.domain.Secret") },
        )
    }

    private companion object {
        const val PERSISTENCE_PACKAGE = "$APP_PACKAGE.infrastructure.persistence"
        val ALLOWED_PUBLIC =
            setOf(
                "PaperclipPersistence",
                "StorageUnavailableException",
                "openJvmPersistence",
                "openIosPersistence",
                "openAndroidPersistence",
            )
        val ALLOWED_IMPORTS =
            listOf(
                "kotlin.",
                "kotlinx.",
                "app.cash.sqldelight.",
                "co.touchlab.sqliter.",
                "androidx.sqlite.",
                "android.",
                "java.",
                "javax.",
                "platform.",
                "$APP_PACKAGE.domain",
                PERSISTENCE_PACKAGE,
            )
        val PUBLIC_CLASSES = Regex("(Sql\\w*|PaperclipPersistence)")
    }
}
