package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Spec acceptance criterion 1: every PRD functional requirement that Phase 1a delivers (spec section 4) is named by at
 * least one test that runs on the JVM, the Android host and the iOS simulator. A test names a requirement with its id
 * (for example `FR-030`) in its name, its KDoc or a comment, in a `commonTest` source set.
 */
class RequirementsTraceabilityTest {
    /** The requirement ids of spec section 4 for Phase 1a. */
    private val phaseOneA: List<Int> =
        listOf(1..8, 10..10, 13..16, 20..25, 30..35, 40..44, 50..52, 60..63, 70..72, 75..75, 78..78, 90..91, 95..97)
            .flatMap { it.toList() }

    /**
     * Requirements with no Phase 1a test, each with the reason. Keep it empty unless a requirement genuinely has none
     * (its status is then also listed in `specs/261101-phase-1a-shared-contracts/status.md`).
     */
    private val exempt: Map<Int, String> = emptyMap()

    private val tag = Regex("""FR-(\d{3})""")

    private fun commonTestSources(): List<File> =
        File(projectRoot, "shared")
            .walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.extension == "kt" && it.invariantSeparatorsPath.contains("/src/commonTest/") }
            .toList()

    private fun taggedIds(): Set<Int> =
        commonTestSources()
            .flatMap { file ->
                tag.findAll(file.readText()).map { it.groupValues[1].toInt() }.toList()
            }.toSet()

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
        const val PHASE_ONE_A_IDS = 47
    }
}
