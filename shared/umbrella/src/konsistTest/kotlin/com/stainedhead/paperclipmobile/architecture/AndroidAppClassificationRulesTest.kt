package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * "Build once" (INTENT principle 5, R-019): which run is live, which buttons a row offers, which colour family a status
 * earns, what a factory's status says and how a decide-by is written are decided in shared presentation. The Compose
 * app only turns the shared answer into a colour or a word. Each rule has a planted-violation test.
 */
class AndroidAppClassificationRulesTest {
    @Test
    fun `the app classifies no status, run, row action or expiry itself`() {
        val sources = androidAppSources(projectRoot)
        Assertions.assertTrue(sources.isNotEmpty(), "No androidApp sources found: the rule would pass vacuously")
        val offenders =
            sources.associate { it.name to appClassifications(it.readText()) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "Use the shared presentation member instead: $offenders")
    }

    @Test
    fun `the classification scan flags copies of the shared rules and allows the shared family to colour lookup`() {
        val source =
            """
            fun RunStatusUi.isLive(): Boolean = this == RunStatusUi.Running
            fun AgentRowUi.visibleActions(): List<AgentActionUi> = actions
            fun DecideByUi.hintText(): String? = null
            fun IssueStatusUi.tone(): StatusTone =
                IssueStatusUi.Blocked -> StatusTone.Critical
                IssueStatusUi.InReview, IssueStatusUi.Todo -> StatusTone.Warn
            expired -> FactoryStatusLabel.KeyExpired to StatusTone.Warn
            data class FactoryStatusPresentation(val label: Int)
            sealed interface ExpiryPresentation
            ToneUi.Success -> StatusTone.Success
            val tone = if (done) StatusTone.Success else StatusTone.Neutral
            // IssueStatusUi.Blocked -> StatusTone.Critical in a comment
            """.trimIndent()

        Assertions.assertEquals(
            listOf(
                "fun RunStatusUi.isLive(): Boolean = this == RunStatusUi.Running",
                "fun AgentRowUi.visibleActions(): List<AgentActionUi> = actions",
                "fun DecideByUi.hintText(): String? = null",
                "fun IssueStatusUi.tone(): StatusTone =",
                "IssueStatusUi.Blocked -> StatusTone.Critical",
                "IssueStatusUi.InReview, IssueStatusUi.Todo -> StatusTone.Warn",
                "expired -> FactoryStatusLabel.KeyExpired to StatusTone.Warn",
                "data class FactoryStatusPresentation(val label: Int)",
                "sealed interface ExpiryPresentation",
            ),
            appClassifications(source),
        )
    }
}
