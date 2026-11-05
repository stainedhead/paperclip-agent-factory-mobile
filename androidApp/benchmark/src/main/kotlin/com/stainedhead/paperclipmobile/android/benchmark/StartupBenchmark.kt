package com.stainedhead.paperclipmobile.android.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STARTUP_ITERATIONS = 10
private const val SCROLL_ITERATIONS = 5

/**
 * The performance guard of the Android PRD (NFR performance): cold start to the cached Inbox (median of 10 runs) and
 * jank while scrolling 200 Inbox cards, on the R8-minified `benchmark` build with the baseline profile installed. On
 * demand only (scripts/run-benchmarks.sh), never in `check`.
 *
 * `timeToInitialDisplayMs` is the first frame; `timeToFullDisplayMs` is when the Inbox reported itself fully drawn
 * (it shows its content), which is the PRD's "to cached Inbox" number. On the demo graph the Inbox content comes from
 * fakes in memory, so this measures the app's own start, not a network.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartToTheInbox() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
        ) {
            launchDemo()
            awaitInboxList()
        }

    @Test
    fun coldStartToTheInboxWithoutAProfile() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
        ) {
            launchDemo()
            awaitInboxList()
        }

    @Test
    fun flingTwoHundredInboxCards() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = SCROLL_ITERATIONS,
            setupBlock = { launchDemo(GENERATED_INBOX_CARDS) },
        ) {
            flingInbox()
        }
}
