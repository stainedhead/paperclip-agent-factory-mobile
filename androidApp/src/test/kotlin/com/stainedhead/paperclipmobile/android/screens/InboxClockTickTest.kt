package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.designsystem.ProvideNow
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecideByUi
import com.stainedhead.paperclipmobile.presentation.InboxGroupUi
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InboxSectionUi
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.presentation.OpenKindUi
import com.stainedhead.paperclipmobile.presentation.OpenTargetUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

private const val CARDS = 4
private const val TICK_MILLIS = 15_000L
private const val ONE_MINUTE = 60_000L

private fun cardAt(
    index: Int,
    activityAt: Long,
) = InboxItemUi(
    id = "tick-$index",
    kind = SourceKindUi.Approval,
    rawKind = null,
    severity = SeverityUi.High,
    title = "Card $index",
    identifier = null,
    whyNow = "Because of $index",
    originAgentName = null,
    activityAtEpochMillis = activityAt,
    decideBy = DecideByUi(DecideByKindUi.Today, null),
    inlineActions = emptyList(),
    open = OpenTargetUi(OpenKindUi.Approval, "ap-$index", null),
    webUrl = null,
    isReadOnly = false,
    audience = null,
    dismissalKey = "attention:tick-$index",
)

/**
 * R-008: the Inbox runs one clock for the whole screen, and a tick of it moves the age labels only. A card's own
 * composition does not run again, so a fling is not slowed by 200 timers or by recomposing every visible card.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2000dp-xhdpi")
class InboxClockTickTest {
    @get:Rule
    val rule = createComposeRule()

    private val inboxCardRuns = AtomicInteger()
    private val updatedLabelRuns = AtomicInteger()

    private val tracer =
        object : CompositionTracer {
            override fun traceEventStart(
                key: Int,
                dirty1: Int,
                dirty2: Int,
                info: String,
            ) {
                if (".InboxCard (" in info) inboxCardRuns.incrementAndGet()
                if (".UpdatedLabel (" in info) updatedLabelRuns.incrementAndGet()
            }

            override fun traceEventEnd() = Unit

            override fun isTraceInProgress() = true
        }

    @After
    fun tearDown() {
        Composer.setTracer(NoTracer)
    }

    @Test
    fun aClockTickRecomposesNoInboxCardButTheAgeLabelsFollowIt() {
        val activityAt = System.currentTimeMillis() - ONE_MINUTE
        val state =
            InboxState(
                phase = InboxPhase.Content,
                sections =
                    listOf(
                        InboxSectionUi(InboxGroupUi.DecideNow, (0 until CARDS).map { cardAt(it, activityAt) }),
                    ),
                badgeCount = CARDS,
                totalCount = CARDS,
                updatedAtEpochMillis = activityAt,
            )
        Composer.setTracer(tracer)
        rule.setContent { PaperclipTheme(darkTheme = false) { ProvideNow { InboxContent(state, {}, {}) } } }
        rule.waitForIdle()
        inboxCardRuns.get() shouldBeGreaterThan 0
        val cardsAfterFirstDraw = inboxCardRuns.get()
        val labelsAfterFirstDraw = updatedLabelRuns.get()

        // The clock reads wall time: make sure some has passed since the first draw.
        val drawnAt = System.currentTimeMillis()
        awaitThat("the wall clock to move on") { System.currentTimeMillis() > drawnAt }
        rule.mainClock.advanceTimeBy(TICK_MILLIS * 2)
        rule.waitForIdle()

        updatedLabelRuns.get() shouldBeGreaterThan labelsAfterFirstDraw
        inboxCardRuns.get() shouldBe cardsAfterFirstDraw
    }

    private object NoTracer : CompositionTracer {
        override fun traceEventStart(
            key: Int,
            dirty1: Int,
            dirty2: Int,
            info: String,
        ) = Unit

        override fun traceEventEnd() = Unit

        override fun isTraceInProgress() = false
    }
}
