package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Release build rules (Android PRD risk 9, NFR Security; ADR-132): R8 is on with the app's own keep rules, `Log` is
 * stripped, the signing key and its passwords come from the environment and never from a file in the repository, and
 * the version comes from the catalog and the commit count.
 */
class AndroidReleaseBuildRulesTest {
    private val buildScript = File(projectRoot, "androidApp/build.gradle.kts").readText()

    private fun block(
        text: String,
        name: String,
    ): String {
        val start = text.indexOf("$name {")
        Assertions.assertTrue(start >= 0, "no `$name {` block in androidApp/build.gradle.kts")
        var depth = 0
        for (i in text.indexOf('{', start) until text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}' && --depth == 0) return text.substring(start, i)
        }
        error("unterminated `$name` block")
    }

    @Test
    fun `the release build is minified and shrunk with the app's own keep rules`() {
        val release = block(block(buildScript, "buildTypes"), "release")
        Assertions.assertTrue("isMinifyEnabled = true" in release, "release does not enable R8")
        Assertions.assertTrue("isShrinkResources = true" in release, "release does not shrink resources")
        Assertions.assertTrue("\"proguard-rules.pro\"" in release, "release does not use proguard-rules.pro")
        Assertions.assertTrue(
            File(projectRoot, "androidApp/proguard-rules.pro").isFile,
            "proguard-rules.pro is missing",
        )
    }

    @Test
    fun `log calls are stripped from release and every keep rule has a reason`() {
        val rules = File(projectRoot, "androidApp/proguard-rules.pro").readText()
        Assertions.assertTrue("-assumenosideeffects class android.util.Log" in rules, "Log is not stripped")
        val blanketKeeps =
            rules.lines().filter {
                it.trim().matches(Regex("""-keep\s+class\s+\*\*.*|-dontobfuscate|-dontoptimize"""))
            }
        Assertions.assertEquals(emptyList<String>(), blanketKeeps, "a blanket rule switches R8 off for the app")
    }

    @Test
    fun `the signing key comes from the environment and no key file or password is committed`() {
        Assertions.assertTrue("PAFM_UPLOAD_STORE_FILE" in buildScript)
        Assertions.assertFalse(
            Regex("""storePassword\s*=\s*"""").containsMatchIn(buildScript),
            "literal store password",
        )
        Assertions.assertFalse(Regex("""keyPassword\s*=\s*"""").containsMatchIn(buildScript), "literal key password")
        val keyFiles =
            projectRoot
                .walkTopDown()
                .onEnter { it.name !in setOf("build", ".gradle", ".git", ".claude", "node_modules") }
                .filter { it.isFile && it.extension in setOf("jks", "keystore", "p12") }
                .map { it.relativeTo(projectRoot).path }
                .toList()
        Assertions.assertEquals(emptyList<String>(), keyFiles, "key material in the repository")
    }

    @Test
    fun `the version name comes from the catalog and the version code from the commit count`() {
        Assertions.assertTrue("libs.versions.android.app" in buildScript, "versionName is not from the catalog")
        Assertions.assertTrue(
            "rev-list" in buildScript && "--count" in buildScript,
            "versionCode is not the commit count",
        )
        Assertions.assertFalse(Regex("""versionName\s*=\s*"""").containsMatchIn(buildScript), "literal versionName")
        Assertions.assertFalse(Regex("""versionCode\s*=\s*\d""").containsMatchIn(buildScript), "literal versionCode")
    }

    @Test
    fun `only the releaseDemo build type turns the demo entry on and it is never the shipping type`() {
        val types = block(buildScript, "buildTypes")
        Assertions.assertFalse("DEMO_ENTRY\", \"true\"" in block(types, "release"), "release has the demo entry")
        Assertions.assertTrue("DEMO_ENTRY\", \"true\"" in block(types, "create(\"releaseDemo\")"))
        Assertions.assertTrue(
            "applicationIdSuffix = \".demo\"" in block(types, "create(\"releaseDemo\")"),
            "demo shares the app id",
        )
    }
}
