package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Rules for the Compose app (AGENTS.md "Clean Architecture": apps render state and forward intents; PRD AND-003).
 * Each rule has a planted-violation test: a rule that cannot fail on a planted violation is not a rule.
 */
class AndroidAppRulesTest {
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
    fun `the app imports no infrastructure package except the platform types the umbrella exposes`() {
        assertClean(::appInfrastructureImports, "androidApp must not import infrastructure adapters")
    }

    @Test
    fun `the infrastructure import scan flags adapters and allows platform types`() {
        val source =
            """
            import com.stainedhead.paperclipmobile.infrastructure.api.KtorGateway
            import com.stainedhead.paperclipmobile.infrastructure.persistence.AppDatabase
            import com.stainedhead.paperclipmobile.infrastructure.securestore.KeystoreSecretStore
            import com.stainedhead.paperclipmobile.infrastructure.platform.SystemClock
            import com.stainedhead.paperclipmobile.domain.Secret
            """.trimIndent()

        Assertions.assertEquals(
            listOf(
                "com.stainedhead.paperclipmobile.infrastructure.api.KtorGateway",
                "com.stainedhead.paperclipmobile.infrastructure.persistence.AppDatabase",
                "com.stainedhead.paperclipmobile.infrastructure.securestore.KeystoreSecretStore",
            ),
            appInfrastructureImports(source),
        )
    }

    @Test
    fun `the app never reveals a secret`() {
        assertClean(::secretReveals, "androidApp must not call Secret.reveal")
    }

    @Test
    fun `the reveal scan flags a call and ignores comments`() {
        val source =
            """
            // never call token.reveal() here
            val raw = token.reveal()
            val also = secret . reveal ()
            """.trimIndent()

        Assertions.assertEquals(
            listOf("val raw = token.reveal()", "val also = secret . reveal ()"),
            secretReveals(source),
        )
    }

    @Test
    fun `the app has no literal text in a Text composable`() {
        assertClean(::literalTexts, "androidApp must take user-facing text from string resources")
    }

    @Test
    fun `the literal text scan flags literal Text calls and allows resources and values`() {
        val source =
            """
            Text("Approve")
            Text(text = "Reject")
            Text( "Pause" )
            Text(stringResource(R.string.approve))
            Text(text = label)
            Text(state.title)
            val x = "Text(\"in a string\")"
            """.trimIndent()

        Assertions.assertEquals(
            listOf("""Text("Approve")""", """Text(text = "Reject")""", """Text( "Pause" )"""),
            literalTexts(source),
        )
    }

    @Test
    fun `composables never call the biometric prompt`() {
        assertClean(::composableBiometricCalls, "A file with composables must not use BiometricPrompt")
    }

    @Test
    fun `the biometric scan flags a composable file that names the prompt and allows a plain class`() {
        val composable =
            """
            @Composable
            fun ApproveButton() {
                val prompt = BiometricPrompt(activity, executor, callback)
            }
            """.trimIndent()
        val plain =
            """
            class AuthHost {
                val prompt = BiometricPrompt(activity, executor, callback)
            }
            """.trimIndent()

        Assertions.assertEquals(
            listOf("val prompt = BiometricPrompt(activity, executor, callback)"),
            composableBiometricCalls(composable),
        )
        Assertions.assertEquals(emptyList<String>(), composableBiometricCalls(plain))
    }
}
