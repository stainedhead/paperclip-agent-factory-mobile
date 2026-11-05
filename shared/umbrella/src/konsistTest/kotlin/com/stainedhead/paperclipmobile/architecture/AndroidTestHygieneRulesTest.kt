package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * How the Compose app's tests keep their evidence honest: a screenshot is compared with no pixel tolerance (R-031), so
 * a changed label, icon or colour fails `verifyRoborazziDebug`. Each rule has a planted-violation test.
 */
class AndroidTestHygieneRulesTest {
    private val baselineHelper = "Baselines.kt"
    private val tests: List<File> =
        File(projectRoot, "androidApp/src/test")
            .walkTopDown()
            .filter { it.extension == "kt" && it.name != baselineHelper }
            .toList()

    private val captureCall = Regex("""\bcaptureRoboImage\s*\(""")
    private val threshold = Regex("""changeThreshold\s*=\s*([0-9.]+)""")

    private fun directCaptures(source: String): List<String> =
        appCodeLines(source).filter { captureCall.containsMatchIn(it) }

    private fun tolerantThresholds(source: String): List<String> =
        appCodeLines(source).filter { line ->
            threshold
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.toFloatOrNull()
                ?.let { it > 0f } == true
        }

    @Test
    fun `a screenshot test captures through captureBaseline, which allows no changed pixel`() {
        Assertions.assertTrue(tests.size > MIN_TESTS, "No androidApp tests found: the rule would pass vacuously")
        val screenshots = tests.filter { "captureBaseline(" in it.readText() }
        Assertions.assertTrue(
            screenshots.size >= MIN_SCREENSHOT_TESTS,
            "The screenshot tests no longer use captureBaseline",
        )
        val offenders = tests.associate { it.name to directCaptures(it.readText()) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(
            offenders.isEmpty(),
            "Use captureBaseline (threshold 0), not captureRoboImage: $offenders",
        )
    }

    @Test
    fun `the helper compares with a threshold of zero and no test loosens it`() {
        val helper = File(projectRoot, "androidApp/src/test/kotlin").walkTopDown().first { it.name == baselineHelper }
        Assertions.assertTrue(
            "changeThreshold = 0f" in helper.readText(),
            "the baseline helper tolerates changed pixels",
        )
        val offenders =
            tests
                .associate {
                    it.name to
                        tolerantThresholds(
                            it.readText(),
                        )
                }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "A screenshot test must not loosen the pixel threshold: $offenders")
    }

    @Test
    fun `the scans flag a direct capture and a loosened threshold and allow the helper and zero`() {
        val source =
            """
            // captureRoboImage( is only named in a comment
            rule.onRoot().captureRoboImage("a.png")
            rule.onRoot().captureBaseline("b.png")
            val loose = CompareOptions(changeThreshold = 0.01f)
            val exact = CompareOptions(changeThreshold = 0f)
            """.trimIndent()

        Assertions.assertEquals(listOf("rule.onRoot().captureRoboImage(\"a.png\")"), directCaptures(source))
        Assertions.assertEquals(
            listOf("val loose = CompareOptions(changeThreshold = 0.01f)"),
            tolerantThresholds(source),
        )
    }

    private companion object {
        const val MIN_TESTS = 20
        const val MIN_SCREENSHOT_TESTS = 7
    }
}
