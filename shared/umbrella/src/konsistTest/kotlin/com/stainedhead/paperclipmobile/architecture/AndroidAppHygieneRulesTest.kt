package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Rules for how the Compose app keeps its code and its state honest: no warning switched off where it fires, and no
 * secret in the saved state that survives process death (AND-019). Each rule has a planted-violation test.
 */
class AndroidAppHygieneRulesTest {
    private val sources = androidAppSources(projectRoot)

    private fun assertClean(
        rule: (String) -> List<String>,
        message: String,
    ) {
        Assertions.assertTrue(sources.isNotEmpty(), "No androidApp sources found: the rule would pass vacuously")
        val offenders = sources.associate { it.name to rule(it.readText()) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "$message: $offenders")
    }

    @Test
    fun `the app suppresses no warning inline`() {
        assertClean(::inlineSuppressions, "androidApp must not suppress lint or compiler warnings inline")
    }

    @Test
    fun `the suppression scan flags every annotation form and ignores comments`() {
        val source =
            """
            // never @Suppress("x") here
            @Suppress("UNCHECKED_CAST")
            @file:Suppress("MagicNumber")
            @SuppressLint("NewApi")
            @SuppressWarnings("unused")
            val fine = "text"
            """.trimIndent()

        Assertions.assertEquals(
            listOf(
                "@Suppress(\"UNCHECKED_CAST\")",
                "@file:Suppress(\"MagicNumber\")",
                "@SuppressLint(\"NewApi\")",
                "@SuppressWarnings(\"unused\")",
            ),
            inlineSuppressions(source),
        )
    }

    @Test
    fun `no secret is kept in saved state`() {
        assertClean(::secretsInSavedState, "androidApp must not save a secret in saved instance state")
    }

    @Test
    fun `the saved state scan flags a secret near a saving API and allows plain text state`() {
        val source =
            """
            var address by rememberSaveable { mutableStateOf("") }
            var key by rememberSaveable { mutableStateOf(secret) }
            handle.set("token", token)
            val saved = savedStateHandle.get<String>("approvalUrl")
            outState.putString("boardKey", boardKey)
            """.trimIndent()

        Assertions.assertEquals(
            listOf(
                "var key by rememberSaveable { mutableStateOf(secret) }",
                "val saved = savedStateHandle.get<String>(\"approvalUrl\")",
                "outState.putString(\"boardKey\", boardKey)",
            ),
            secretsInSavedState(source),
        )
    }

    @Test
    fun `the app opens no web address through the platform uri handler`() {
        assertClean(
            ::uriHandlerUse,
            "androidApp must open web addresses through WebOpener (https only), not LocalUriHandler",
        )
    }

    @Test
    fun `the uri handler scan flags the handler and the local and ignores comments`() {
        val source =
            """
            // LocalUriHandler is not used here
            import androidx.compose.ui.platform.LocalUriHandler
            val uriHandler = LocalUriHandler.current
            uriHandler.openUri(url)
            val opener = currentWebOpener()
            """.trimIndent()

        Assertions.assertEquals(
            listOf(
                "import androidx.compose.ui.platform.LocalUriHandler",
                "val uriHandler = LocalUriHandler.current",
                "uriHandler.openUri(url)",
            ),
            uriHandlerUse(source),
        )
    }
}
