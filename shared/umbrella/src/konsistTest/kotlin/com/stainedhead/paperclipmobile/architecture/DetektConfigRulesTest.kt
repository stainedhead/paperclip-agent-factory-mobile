package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `config/detekt/detekt.yml` says deviations are listed "with the reason" (F-C-13). Every rule block, and `build`,
 * has a comment line directly above it, so a loosened rule cannot be added silently.
 */
class DetektConfigRulesTest {
    private val config = File(projectRoot, "config/detekt/detekt.yml")

    /** The indented `Name:` lines (rule blocks and the `build` block) that have no `#` line right above them. */
    private fun withoutReason(text: String): List<String> {
        val lines = text.lines()
        return lines
            .withIndex()
            .filter { (_, line) -> Regex("""^( {2}[A-Z]\w*|build):\s*$""").matches(line) }
            .filter { (index, _) -> !lines.getOrElse(index - 1) { "" }.trim().startsWith("#") }
            .map { (_, line) -> line.trim().removeSuffix(":") }
    }

    @Test
    fun `every deviation in the detekt config has a reason comment`() {
        Assertions.assertTrue(config.exists(), "config/detekt/detekt.yml not found")
        val text = config.readText()
        Assertions.assertTrue(Regex("""(?m)^ {2}[A-Z]\w*:\s*$""").findAll(text).count() >= MIN_RULES, "No rules found")
        val missing = withoutReason(text)
        Assertions.assertTrue(missing.isEmpty(), "These detekt rules have no reason comment above them: $missing")
    }

    @Test
    fun `a rule without a comment above it is reported`() {
        val sample = "style:\n  # reason\n  MagicNumber:\n    a: 1\n  ReturnCount:\n    max: 4\n"

        Assertions.assertEquals(listOf("ReturnCount"), withoutReason(sample))
    }

    private companion object {
        const val MIN_RULES = 5
    }
}
