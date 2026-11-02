package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Spec acceptance criterion 1: every PRD functional requirement that Phase 1a delivers (spec section 4) is named by at
 * least one test that runs on the JVM, the Android host and the iOS simulator.
 *
 * The convention (F-C-5, F-D-5): a test names a requirement by putting its id (for example `FR-030`) in the
 * backtick name of a function annotated `@Test` in a `commonTest` source set. A mention in a comment, a KDoc, a
 * string or the name of a helper does not count, so the tag cannot outlive the test it describes. A tag is a claim
 * that the test exercises the requirement; when only the Phase 1a part of a requirement is exercised the id is also
 * listed in [partial] with what is left for later.
 */
class RequirementsTraceabilityTest {
    /** The requirement ids of spec section 4 for Phase 1a. */
    private val phaseOneA: List<Int> =
        listOf(1..8, 10..10, 13..16, 20..25, 30..35, 40..44, 50..52, 60..63, 70..72, 75..75, 78..78, 90..91, 95..97)
            .flatMap { it.toList() }

    /**
     * Requirements with no Phase 1a test, each with the reason. Keep it empty unless a requirement genuinely has none
     * (its status is then also listed in `specs/archive/261101-phase-1a-shared-contracts/status.md`).
     */
    private val exempt: Map<Int, String> = emptyMap()

    /**
     * Requirements whose tagged tests cover only the part Phase 1a builds. Each still needs a tag; the entry says what
     * is not covered yet. Also listed in the review-fix `status.md`.
     */
    private val partial: Map<Int, String> =
        mapOf(
            2 to "the SQLDelight store and backup exclusion are Phase 1b; the port contract runs against the fake",
            8 to "only the deep link is built and tested; the QR code scanner is a UI feature of Phase 1c",
            13 to "the per-instance secret store contract runs against the fake; Keychain/Keystore paths are Phase 1b",
            16 to "only the pin format is validated; a pin mismatch blocking requests is the Phase 1b HTTP client",
            97 to "domain error to error key is tested; the {error, details} JSON to DomainError mapping is Phase 1b",
        )

    private val tag = Regex("""FR-(\d{3})""")

    /** The backtick name of every function annotated `@Test`. */
    private val testName = Regex("""@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*(?:suspend\s+)?fun\s+`([^`]+)`""")

    /** The requirement ids named by a `@Test` function name in [source]; comments and strings do not count. */
    private fun idsInTestNames(source: String): Set<Int> =
        testName
            .findAll(source)
            .flatMap { name -> tag.findAll(name.groupValues[1]).map { it.groupValues[1].toInt() } }
            .toSet()

    private fun commonTestSources(): List<File> =
        File(projectRoot, "shared")
            .walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.extension == "kt" && it.invariantSeparatorsPath.contains("/src/commonTest/") }
            .toList()

    private fun taggedIds(): Set<Int> =
        commonTestSources()
            .flatMap { file -> idsInTestNames(file.readText()) }
            .toSet()

    @Test
    fun `every Phase 1a requirement is named by a common test`() {
        val tagged = taggedIds()
        val missing = phaseOneA.filter { it !in tagged && it !in exempt }
        Assertions.assertTrue(
            missing.isEmpty(),
            "Requirements with no test tag: ${missing.map { "FR-%03d".format(it) }}",
        )
    }

    @Test
    fun `a partial requirement is still tagged and is a Phase 1a requirement`() {
        val tagged = taggedIds()
        Assertions.assertTrue(partial.keys.all { it in phaseOneA }, "A partial id must be a Phase 1a requirement")
        Assertions.assertTrue(partial.keys.all { it in tagged }, "A partial requirement needs its (partial) test tag")
        Assertions.assertTrue(partial.values.all { it.isNotBlank() }, "A partial requirement states what is left")
    }

    @Test
    fun `a mention in a comment or a string does not count as a test tag`() {
        val source =
            """
            /** FR-001 is described here. */
            // FR-002 in a line comment
            class Helper {
                val label = "FR-003"
                fun `FR-004 helper without the annotation`() = Unit

                @Test
                fun `FR-005 a real test name`() = Unit

                @Test
                @Ignore("FR-007 in an annotation argument")
                fun `FR-006 second annotation then the name`() = Unit
            }
            """.trimIndent()

        Assertions.assertEquals(setOf(FIFTH, SIXTH), idsInTestNames(source))
    }

    @Test
    fun `an exemption is removed once the requirement has a test`() {
        val tagged = taggedIds()
        val stale = exempt.keys.filter { it in tagged }
        Assertions.assertTrue(stale.isEmpty(), "These requirements are tagged now, remove their exemption: $stale")
    }

    @Test
    fun `the traceability rule can see the tests it guards`() {
        Assertions.assertTrue(commonTestSources().size > MIN_TEST_SOURCES, "Expected many common test sources")
        Assertions.assertEquals(PHASE_ONE_A_IDS, phaseOneA.size, "Spec section 4 lists 47 Phase 1a requirement ids")
    }

    private companion object {
        const val MIN_TEST_SOURCES = 50
        const val FIFTH = 5
        const val SIXTH = 6
        const val PHASE_ONE_A_IDS = 47
    }
}
