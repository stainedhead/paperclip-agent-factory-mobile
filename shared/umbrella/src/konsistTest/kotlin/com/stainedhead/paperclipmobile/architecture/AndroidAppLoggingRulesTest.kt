package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/** The app writes to the system log through `AppLog` only (AND-018), with a planted-violation test for the rule. */
class AndroidAppLoggingRulesTest {
    private val sources = androidAppSources(projectRoot)

    @Test
    fun `only the log wrapper writes to the system log`() {
        val offenders =
            sources
                .filter { it.name != "AppLog.kt" }
                .associate { it.name to directLogUse(it.readText()) }
                .filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "Log through AppLog (AND-018), not android.util.Log: $offenders")
    }

    @Test
    fun `the log scan flags the system log and ignores comments and the wrapper`() {
        val source =
            """
            import android.util.Log
            // Log.d("x", "in a comment")
            Log.d("graph", "open")
            Log.w("graph", "open")
            log.debug("graph", "through the wrapper")
            Catalog.d("not the system log")
            """.trimIndent()

        Assertions.assertEquals(
            listOf("import android.util.Log", """Log.d("graph", "open")""", """Log.w("graph", "open")"""),
            directLogUse(source),
        )
    }
}
