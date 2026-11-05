package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * CI supply chain (F-C-7): every third-party action is pinned to a full commit SHA with its version in a comment,
 * and every container image used by CI is pinned by digest. Dependabot (`github-actions` and `docker` ecosystems)
 * keeps both current.
 */
class CiPinningRulesTest {
    private val workflows: List<File> =
        File(projectRoot, ".github/workflows").listFiles { file -> file.extension == "yml" }.orEmpty().toList()

    private val usesLine = Regex("""^\s*-?\s*uses:\s*(\S+)(.*)$""")
    private val pinned = Regex("""^[\w.-]+/[\w./-]+@[0-9a-f]{40}$""")

    private fun unpinnedActions(text: String): List<String> =
        text
            .lines()
            .mapNotNull { usesLine.matchEntire(it) }
            .filter { !it.groupValues[1].startsWith("./") }
            .filter { !pinned.matches(it.groupValues[1]) || !it.groupValues[2].trim().startsWith("# ") }
            .map { it.groupValues[1] }

    @Test
    fun `every action is pinned to a full commit SHA with its version in a comment`() {
        Assertions.assertTrue(workflows.size >= MIN_WORKFLOWS, "No workflows found: the rule would pass vacuously")
        val offenders =
            workflows
                .associate {
                    it.name to
                        unpinnedActions(
                            it.readText(),
                        )
                }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(
            offenders.isEmpty(),
            "Actions not pinned to a 40 character SHA with a # version: $offenders",
        )
    }

    @Test
    fun `the pin check flags a moving tag and a missing version comment`() {
        val sample =
            """
            - uses: actions/checkout@v7
            - uses: actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9
            - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6
            - uses: ./local-action
            """.trimIndent()

        Assertions.assertEquals(
            listOf("actions/checkout@v7", "actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9"),
            unpinnedActions(sample),
        )
    }

    @Test
    fun `workflows and Dockerfiles use no floating image tag`() {
        val dockerfiles = File(projectRoot, "docker").walkTopDown().filter { it.name == "Dockerfile" }.toList()
        Assertions.assertTrue(dockerfiles.isNotEmpty(), "No Dockerfile found: the rule would pass vacuously")
        dockerfiles.forEach { file ->
            val from = file.readLines().filter { it.startsWith("FROM ") }
            Assertions.assertTrue(
                from.isNotEmpty() && from.all { Regex("""@sha256:[0-9a-f]{64}""").containsMatchIn(it) },
                "${file.name} must pin every base image by digest: $from",
            )
        }
        val floating = workflows.filter { it.readText().contains(":latest") }.map { it.name }
        Assertions.assertTrue(floating.isEmpty(), "Workflows must not run a :latest image: $floating")
    }

    /** The `group:` expression of the workflow's top-level concurrency block, or null when it has none. */
    private fun concurrencyGroup(text: String): String? {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trimEnd() == "concurrency:" }
        if (start < 0) return null
        return lines
            .drop(start + 1)
            .takeWhile { it.startsWith(" ") }
            .firstOrNull { it.trim().startsWith("group:") }
            ?.substringAfter("group:")
            ?.trim()
    }

    /** A scheduled run must not share a group with a push to the same ref, or each cancels the other (R-016). */
    private fun groupSeparatesScheduledRuns(text: String): Boolean {
        val group = concurrencyGroup(text) ?: return true
        val triggers = text.lines().takeWhile { it.trimEnd() != "concurrency:" }.joinToString("\n")
        return "schedule:" !in triggers || EVENT_NAME in group
    }

    @Test
    fun `a workflow with a nightly schedule keeps its scheduled runs in their own concurrency group`() {
        val android = File(projectRoot, ".github/workflows/android.yml").readText()
        Assertions.assertTrue("schedule:" in android, "android.yml has no schedule: the rule would pass vacuously")
        workflows.forEach {
            Assertions.assertTrue(
                groupSeparatesScheduledRuns(it.readText()),
                "${it.name}: the concurrency group must include $EVENT_NAME, or a nightly run cancels a push run",
            )
        }
    }

    @Test
    fun `the concurrency check flags a scheduled workflow whose group is only the ref`() {
        val head = "on:\n  push:\n  schedule:\n    - cron: '17 3 * * *'\n\nconcurrency:\n  group: "
        Assertions.assertFalse(
            groupSeparatesScheduledRuns(head + "android-\${{ github.ref }}\n  cancel-in-progress: true\n"),
        )
        Assertions.assertTrue(
            groupSeparatesScheduledRuns(
                head + "android-\${{ $EVENT_NAME == 'schedule' && 'nightly' || github.ref }}\n",
            ),
        )
        Assertions.assertTrue(
            groupSeparatesScheduledRuns("on:\n  push:\nconcurrency:\n  group: g-\${{ github.ref }}\n"),
        )
    }

    private companion object {
        const val EVENT_NAME = "github.event_name"
        const val MIN_WORKFLOWS = 4
    }
}
