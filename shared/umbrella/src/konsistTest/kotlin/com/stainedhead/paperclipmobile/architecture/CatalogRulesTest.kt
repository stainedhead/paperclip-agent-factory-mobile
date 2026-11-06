package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * AGENTS.md "Toolchain": a dependency or plugin version is never hard-coded in a build script; it comes from
 * `gradle/libs.versions.toml`. The one exception is the settings plugin block, which cannot read the catalog.
 */
class CatalogRulesTest {
    private val coordinateWithVersion = Regex("""["'][\w.-]+:[\w.-]+:[\w.${'$'}{}-]+["']""")
    private val pluginVersion = Regex("""\bversion\s+["'][^"']+["']""")

    private fun literalVersions(
        text: String,
        allowPluginVersion: Boolean,
    ): List<String> =
        text
            .lines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") }
            .filter {
                coordinateWithVersion.containsMatchIn(it) ||
                    (!allowPluginVersion && pluginVersion.containsMatchIn(it))
            }

    private fun buildScripts(): List<File> =
        projectRoot
            .walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != ".git" && it.name != ".claude" }
            .filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .toList()

    @Test
    fun `no build script hard-codes a dependency or plugin version`() {
        val scripts = buildScripts()
        Assertions.assertTrue(
            scripts.any { it.path.contains("androidApp") },
            "androidApp/build.gradle.kts was not scanned: the rule would pass vacuously",
        )
        val offenders =
            scripts
                .associate { script ->
                    // settings.gradle.kts declares the foojay resolver, which cannot use the catalog.
                    script.relativeTo(projectRoot).path to
                        literalVersions(script.readText(), allowPluginVersion = script.name == "settings.gradle.kts")
                }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "Hard-coded versions (use libs.versions.toml): $offenders")
    }

    @Test
    fun `the version scan flags a coordinate with a version and a plugin version but not a catalog reference`() {
        val planted =
            """
            dependencies {
                implementation("androidx.core:core-ktx:1.15.0")
                implementation(libs.androidx.core)
                implementation(platform(libs.androidx.compose.bom))
            }
            plugins { id("com.example.plugin") version "1.2.3" }
            // implementation("a.b:c:9.9.9") is only a comment
            """.trimIndent()

        Assertions.assertEquals(
            listOf(
                """implementation("androidx.core:core-ktx:1.15.0")""",
                """plugins { id("com.example.plugin") version "1.2.3" }""",
            ),
            literalVersions(planted, allowPluginVersion = false),
        )
        Assertions.assertEquals(
            listOf("""implementation("androidx.core:core-ktx:1.15.0")"""),
            literalVersions(planted, allowPluginVersion = true),
        )
    }
}
