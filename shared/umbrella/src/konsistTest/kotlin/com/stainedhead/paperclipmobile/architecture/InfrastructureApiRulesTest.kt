package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The API adapter module (`shared/infrastructure/api`) keeps the wire format to itself (Phase 1b AD-014): DTOs are
 * internal, live in the `dto` package and are never named anywhere else, and the only public things are the gateway
 * adapters, the HTTP client and its factory.
 */
class InfrastructureApiRulesTest {
    private val apiRoot = File(projectRoot, "shared/infrastructure/api")

    /** Public declarations of the api sources under [root] that are not part of the allowed surface. */
    private fun surfaceOffenders(root: File): List<String> =
        mainSources(root)
            .filter { "/dto/" !in it.invariantSeparatorsPath }
            .flatMap { file -> publicTopLevel(file.readText()).map { "${file.name}: $it" } }
            .filterNot { entry ->
                val name = entry.substringAfter(": ")
                name.endsWith("GatewayAdapter") || name in ALLOWED_PUBLIC
            }

    @Test
    fun `the api module exposes only gateway adapters, the http client and its factory`() {
        val sources = mainSources(apiRoot)
        Assertions.assertTrue(sources.isNotEmpty(), "No api sources found: the rule would pass vacuously")
        val offenders = surfaceOffenders(apiRoot)
        Assertions.assertTrue(offenders.isEmpty(), "Public declarations outside the api surface: $offenders")
    }

    @Test
    fun `every DTO lives in the dto package and is internal`() {
        val dtoFiles = mainSources(apiRoot).filter { "/dto/" in it.invariantSeparatorsPath }
        Assertions.assertTrue(dtoFiles.isNotEmpty(), "No DTO sources found: the rule would pass vacuously")
        val offenders = dtoFiles.flatMap { file -> publicTopLevel(file.readText()).map { "${file.name}: $it" } }
        Assertions.assertTrue(offenders.isEmpty(), "DTO declarations must be internal: $offenders")
        val serializable =
            mainSources(apiRoot)
                .filter { "/dto/" !in it.invariantSeparatorsPath }
                .filter { "@Serializable" in it.readText() }
                .map { it.name }
        Assertions.assertTrue(serializable.isEmpty(), "@Serializable types belong in the dto package: $serializable")
    }

    @Test
    fun `no DTO is referenced outside the api module`() {
        val outside =
            File(projectRoot, "shared")
                .walkTopDown()
                .onEnter { it.name != "build" && it != apiRoot }
                .filter { it.extension == "kt" }
                .filter { "$API_PACKAGE.dto" in it.readText() }
                .map { it.relativeTo(projectRoot).invariantSeparatorsPath }
                .toList()
        Assertions.assertTrue(outside.isEmpty(), "DTO types must stay in infrastructure/api: $outside")
    }

    @Test
    fun `the api module imports only domain, the standard library, Ktor, serialization and the platform`() {
        val offenders =
            mainSources(apiRoot).flatMap { file ->
                disallowedImports(file.readText(), ALLOWED_IMPORTS).map { "${file.name}: $it" }
            }
        Assertions.assertTrue(offenders.isEmpty(), "Unexpected imports in infrastructure/api: $offenders")
    }

    @Test
    fun `the checks see a public declaration and a stray import`() {
        val sample = "class Leak\ninternal class Fine\nprivate fun x() {}\nfun createThing() {}\nobject Open\n"
        Assertions.assertEquals(listOf("Leak", "createThing", "Open"), publicTopLevel(sample))
        Assertions.assertEquals(
            listOf("$APP_PACKAGE.application.SignOut"),
            disallowedImports(
                "import $APP_PACKAGE.domain.Secret\nimport $APP_PACKAGE.application.SignOut\n",
                ALLOWED_IMPORTS,
            ),
        )
    }

    @Test
    fun `the surface rule fails on a planted suspend fun, const val and public class in a real source tree`() {
        val root =
            kotlin.io.path
                .createTempDirectory("pafm-api-surface")
                .toFile()
        try {
            val dir = File(root, "src/commonMain/kotlin").also { it.mkdirs() }
            File(dir, "Planted.kt").writeText(
                "suspend fun leakToken() {}\nconst val DEFAULT_KEY = \"x\"\npublic class Loud\n" +
                    "internal class Fine\nclass RealGatewayAdapter\n",
            )
            Assertions.assertEquals(
                listOf("Planted.kt: leakToken", "Planted.kt: DEFAULT_KEY", "Planted.kt: Loud"),
                surfaceOffenders(root),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        const val API_PACKAGE = "$APP_PACKAGE.infrastructure.api"
        val ALLOWED_PUBLIC = setOf("PaperclipHttpClient", "createPaperclipHttpClient", "CallbackPairingBrowser")
        val ALLOWED_IMPORTS =
            listOf(
                "kotlin.",
                "kotlinx.",
                "io.ktor.",
                "java.",
                "javax.",
                "platform.",
                "$APP_PACKAGE.domain",
                API_PACKAGE,
            )
    }
}
