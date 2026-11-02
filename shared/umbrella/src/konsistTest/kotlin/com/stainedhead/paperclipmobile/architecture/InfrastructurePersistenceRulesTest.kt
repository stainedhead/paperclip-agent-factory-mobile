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

    private fun mainSources(root: File): List<File> =
        File(root, "src")
            .listFiles { f -> f.isDirectory && f.name.endsWith("Main") }
            .orEmpty()
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }

    private fun publicTopLevel(source: String): List<String> =
        source
            .lines()
            .filter { TOP_LEVEL.containsMatchIn(it) && !NOT_PUBLIC.containsMatchIn(it) }
            .map { TOP_LEVEL.find(it)!!.groupValues[1] }

    private fun disallowedImports(
        source: String,
        allowed: List<String>,
    ): List<String> =
        source
            .lines()
            .map { it.trim() }
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").trim() }
            .filter { import -> allowed.none { import.startsWith(it) } }

    @Test
    fun `the persistence module exposes only the SQL adapters, their holder and the factories`() {
        val sources = mainSources(persistenceRoot)
        Assertions.assertTrue(sources.isNotEmpty(), "No persistence sources found: the rule would pass vacuously")
        val offenders =
            sources
                .flatMap { file -> publicTopLevel(file.readText()).map { "${file.name}: $it" } }
                .filterNot { it.substringAfter(": ").let { name -> name.startsWith("Sql") || name in ALLOWED_PUBLIC } }
        Assertions.assertTrue(offenders.isEmpty(), "Public declarations outside the persistence surface: $offenders")
    }

    @Test
    fun `public classes build only from internal constructors so generated types never show in a signature`() {
        val offenders =
            mainSources(persistenceRoot)
                .filter { it.readText().contains(Regex("^class (Sql|PaperclipPersistence)", RegexOption.MULTILINE)) }
                .filterNot { it.readText().contains("internal constructor(") }
                .map { it.name }
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
            setOf("PaperclipPersistence", "openJvmPersistence", "openIosPersistence", "openAndroidPersistence")
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
        private const val MODIFIERS = "(?:data |enum |sealed |abstract |open |value )*"
        val TOP_LEVEL =
            Regex(
                "^$MODIFIERS(?:class|object|interface|fun|val|var|typealias)\\s+(?:<[^>]*>\\s*)?(?:[\\w.]+\\.)?(\\w+)",
            )
        val NOT_PUBLIC = Regex("^(?:internal|private|expect internal|actual internal)\\b")
    }
}
