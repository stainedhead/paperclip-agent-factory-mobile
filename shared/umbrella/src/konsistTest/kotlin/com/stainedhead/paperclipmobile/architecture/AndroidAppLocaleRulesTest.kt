package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Locale rules for the Compose app (R-029): a sentence is never glued from parts in code, because the punctuation and
 * the order are a translator's. The planted-violation test proves the scan; the file list is the files fixed for it.
 */
class AndroidAppLocaleRulesTest {
    private val fixedFiles = listOf("BudgetSections.kt", "DecisionSections.kt", "InboxPresentation.kt")

    @Test
    fun `the budget, decision and inbox presentation files glue no sentence in code`() {
        val sources = androidAppSources(projectRoot).filter { it.name in fixedFiles }
        Assertions.assertEquals(fixedFiles.sorted(), sources.map { it.name }.sorted(), "A file moved: update the list")
        val offenders = sources.associate { it.name to gluedSentences(it.readText()) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "Take punctuation and order from string resources: $offenders")
    }

    @Test
    fun `the glue scan flags templates with punctuation between values and hard-wired separators`() {
        val source =
            """
            .semantics { contentDescription = "${'$'}title. ${'$'}help" }
            "${'$'}{confirmText(state)}. ${'$'}{stringResource(R.string.asks)}"
            val line = "${'$'}selected: ${'$'}{it.label}"
            listOfNotNull(kind, id).joinToString(" · ")
            private const val SEPARATOR = " · "
            // "${'$'}a. ${'$'}b" in a comment
            val fine = stringResource(R.string.spoken, title, help)
            val clock = "${'$'}hours:${'$'}{pad(minutes)}"
            val id = "decision-input-${'$'}{input.id}"
            """.trimIndent()

        Assertions.assertEquals(
            listOf(
                ".semantics { contentDescription = \"${'$'}title. ${'$'}help\" }",
                "\"${'$'}{confirmText(state)}. ${'$'}{stringResource(R.string.asks)}\"",
                "val line = \"${'$'}selected: ${'$'}{it.label}\"",
                "listOfNotNull(kind, id).joinToString(\" · \")",
                "private const val SEPARATOR = \" · \"",
            ),
            gluedSentences(source),
        )
    }
}
