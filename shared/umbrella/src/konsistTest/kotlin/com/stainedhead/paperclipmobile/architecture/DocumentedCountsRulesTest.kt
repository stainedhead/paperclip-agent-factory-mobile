package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * F-C-7: a number written in the docs about the rules themselves must be the real one. The Konsist count quoted in
 * `docs/technical-details.md` is compared with the `@Test` methods (declared at class-member indentation) in this
 * folder, so adding a rule without updating the docs fails the build instead of leaving a stale number.
 */
class DocumentedCountsRulesTest {
    private val rulesDir = File(projectRoot, "shared/umbrella/src/konsistTest/kotlin")
    private val technicalDetails = File(projectRoot, "docs/technical-details.md")

    /** (tests, files) as they are in [dir] right now. */
    private fun actualCounts(dir: File): Pair<Int, Int> {
        val files =
            dir
                .walkTopDown()
                .filter {
                    it.extension == "kt" &&
                        TEST_ANNOTATION.containsMatchIn(
                            it.readText(),
                        )
                }.toList()
        return files.sumOf { TEST_ANNOTATION.findAll(it.readText()).count() } to files.size
    }

    /** Every "N tests in M files" or "N in M files" pair a document states, in the order written. */
    private fun quotedCounts(text: String): List<Pair<Int, Int>> =
        QUOTED
            .findAll(text)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toList()

    @Test
    fun `the Konsist count quoted in the technical details is the real count`() {
        val (tests, files) = actualCounts(rulesDir)
        Assertions.assertTrue(tests > 0 && files > 0, "No Konsist tests found: the rule would pass vacuously")
        val quoted = quotedCounts(technicalDetails.readText())
        Assertions.assertTrue(quoted.isNotEmpty(), "technical-details.md quotes no Konsist count")
        Assertions.assertTrue(
            quoted.all { it == tests to files },
            "technical-details.md says $quoted but konsistTest has $tests tests in $files files",
        )
    }

    @Test
    fun `the count scan reads a stale and a current statement and counts planted tests`() {
        Assertions.assertEquals(
            listOf(STALE, CURRENT),
            quotedCounts(
                "fail the build. ${STALE.first} tests in ${STALE.second} files:\n" +
                    "| Konsist (`konsistTest`) | ${CURRENT.first} in ${CURRENT.second} files |",
            ),
        )
        val dir =
            kotlin.io.path
                .createTempDirectory("pafm-konsist-count")
                .toFile()
        try {
            File(dir, "A.kt").writeText("class A {\n    @Test\n    fun a() {}\n\n    @Test\n    fun b() {}\n}\n")
            // A sample inside a raw string is indented deeper and must not count.
            File(
                dir,
                "B.kt",
            ).writeText("class B {\n    @Test fun c() {}\n    val s = \"\"\"\n        @Test\n    \"\"\"\n}\n")
            File(dir, "C.kt").writeText("fun notATest() {}\n")
            Assertions.assertEquals(PLANTED, actualCounts(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the ADR range quoted in the README and the technical details ends at the last ADR`() {
        val last =
            Regex("^## ADR-(\\d+):", RegexOption.MULTILINE)
                .findAll(File(projectRoot, "docs/architectural-decision-record.md").readText())
                .maxOf { it.groupValues[1].toInt() }
        listOf("README.md", "docs/technical-details.md").forEach { name ->
            val ends =
                Regex("ADR-(?:001|121) to ADR-(\\d+)")
                    .findAll(File(projectRoot, name).readText())
                    .map { it.groupValues[1].toInt() }
                    .toList()
            Assertions.assertTrue(ends.isNotEmpty(), "$name quotes no ADR range")
            Assertions.assertTrue(ends.all { it == last }, "$name ends its ADR range at $ends, the last ADR is $last")
        }
    }

    @Test
    fun `phrases that were found false are gone from the docs and the code that carried them`() {
        val falsehoods =
            mapOf(
                "androidApp/src/main/kotlin/com/stainedhead/paperclipmobile/android/screens/DetailSupport.kt" to
                    listOf("15 seconds while the item can change"),
                "specs/archive/261104-phase-2a-android-ui/paperclip-agent-factory-android-PRD.md" to
                    listOf("is reserved in the manifest"),
                "docs/technical-details.md" to listOf("acceptance criterion 8 in virtual time"),
                "docs/product-details.md" to listOf("within one 5 s virtual cycle"),
            )
        falsehoods.forEach { (path, phrases) ->
            val text = File(projectRoot, path).readText()
            phrases.forEach { Assertions.assertFalse(it in text, "$path still says \"$it\"") }
        }
    }

    @Test
    fun `the archived Android status maps every criterion and names only test classes that exist`() {
        val status = File(projectRoot, "specs/archive/261104-phase-2a-android-ui/status.md").readText()
        Assertions.assertFalse("[TBD]" in status, "the acceptance mapping still has a [TBD]")
        val sources =
            listOf("androidApp", "shared", "iosApp")
                .flatMap { File(projectRoot, it).walkTopDown().onEnter { d -> d.name != "build" }.toList() }
                .filter { it.extension == "kt" || it.extension == "swift" }
                .map { it.nameWithoutExtension }
                .toSet()
        val named = Regex("`([A-Z][A-Za-z0-9]*Tests?)[.`]").findAll(status).map { it.groupValues[1] }.toSet()
        Assertions.assertTrue(
            named.size > MIN_NAMED_CLASSES,
            "the scan found only ${named.size} test classes: it would pass vacuously",
        )
        Assertions.assertEquals(emptySet<String>(), named - sources, "classes named in status.md that do not exist")
    }

    private companion object {
        const val MIN_NAMED_CLASSES = 20
        val STALE = 37 to 10
        val CURRENT = 71 to 17
        val PLANTED = 3 to 2
        val TEST_ANNOTATION = Regex("^ {4}@Test\\b", RegexOption.MULTILINE)
        val QUOTED = Regex("(\\d+)(?: tests)? in (\\d+) files")
    }
}
