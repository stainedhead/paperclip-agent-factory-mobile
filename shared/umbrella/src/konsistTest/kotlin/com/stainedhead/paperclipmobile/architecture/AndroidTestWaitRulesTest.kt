package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * How the Compose app's tests wait. The state holders answer on other threads and Compose catches up afterwards, so a
 * test that asserts right after a tap, or that sleeps for a fixed time, passes locally and flakes on a loaded CI
 * runner. Each rule has a planted-violation test.
 */
class AndroidTestWaitRulesTest {
    private val tests: List<File> =
        listOf("test", "androidTest")
            .map { File(projectRoot, "androidApp/src/$it") }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }

    private val sleep = Regex("""\bThread\s*\.\s*sleep\s*\(""")
    private val settleDelay = Regex("""\bdelay\s*\(\s*[A-Z_]*SETTLE[A-Z_]*\s*\)""")
    private val tap = Regex("""\.performClick\s*\(\)\s*$|^tap\s*\(.*\)\s*$""")
    private val bareAssertion = Regex("""\.(?:assertIsDisplayed|assertExists)\s*\(\s*\)\s*$""")

    /** Fixed sleeps and `delay(…SETTLE…)` pauses: wait for the condition (`awaitThat`, `awaitDisplayed`, …) instead. */
    private fun fixedPauses(source: String): List<String> =
        appCodeLines(source).filter { sleep.containsMatchIn(it) || settleDelay.containsMatchIn(it) }

    /** A tap whose next code line asserts a node is displayed or exists, before anything waited for it. */
    private fun assertionRightAfterTap(source: String): List<String> {
        val lines = appCodeLines(source).filter { it.isNotEmpty() }
        return lines
            .zipWithNext()
            .filter { (first, second) -> tap.containsMatchIn(first) && bareAssertion.containsMatchIn(second) }
            .map { (first, second) -> "$first -> $second" }
    }

    @Test
    fun `no androidApp test sleeps or pauses for a fixed settle time`() {
        Assertions.assertTrue(tests.size > MIN_TESTS, "No androidApp tests found: the rule would pass vacuously")
        val offenders = tests.associate { it.name to fixedPauses(it.readText()) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(
            offenders.isEmpty(),
            "Wait on the condition with awaitThat or an await helper, never a fixed pause: $offenders",
        )
    }

    @Test
    fun `no androidApp test asserts a node is displayed right after a tap`() {
        Assertions.assertTrue(tests.size > MIN_TESTS, "No androidApp tests found: the rule would pass vacuously")
        val offenders =
            tests.associate { it.name to assertionRightAfterTap(it.readText()) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(
            offenders.isEmpty(),
            "A sheet or dialog opens after the holder answers: use awaitDisplayed or awaitTextDisplayed: $offenders",
        )
    }

    @Test
    fun `the scans flag a sleep, a settle pause and an assertion right after a tap and allow waited forms`() {
        val source =
            """
            // Thread.sleep(5) is only named in a comment
            Thread.sleep(5)
            runBlocking { delay(SETTLE_MILLIS) }
            runBlocking { delay(QUIET_MILLIS) }
            rule.onNodeWithTag("a").performClick()
            rule.onNodeWithTag("sheet").assertIsDisplayed()
            rule.onNodeWithTag("b").performClick()
            rule.awaitDisplayed("sheet")
            rule.onNodeWithTag("sheet").assertIsDisplayed()
            tap("c")

            rule.onNodeWithTag("d").assertExists()
            """.trimIndent()

        Assertions.assertEquals(
            listOf("Thread.sleep(5)", "runBlocking { delay(SETTLE_MILLIS) }"),
            fixedPauses(source),
        )
        Assertions.assertEquals(
            listOf(
                "rule.onNodeWithTag(\"a\").performClick() -> rule.onNodeWithTag(\"sheet\").assertIsDisplayed()",
                "tap(\"c\") -> rule.onNodeWithTag(\"d\").assertExists()",
            ),
            assertionRightAfterTap(source),
        )
    }

    private companion object {
        const val MIN_TESTS = 20
    }
}
