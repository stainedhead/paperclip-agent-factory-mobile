package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
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
import com.stainedhead.paperclipmobile.presentation.InlineActionUi
import com.stainedhead.paperclipmobile.presentation.OpenKindUi
import com.stainedhead.paperclipmobile.presentation.OpenTargetUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

private const val CARDS = 40

/** The list's header and the section header come before the first card. */
private const val LEADING_ITEMS = 2
private const val ACTIVITY_AT = 1_700_000_000_000L

private fun cardAt(index: Int) =
    InboxItemUi(
        id = "scroll-$index",
        kind = SourceKindUi.Approval,
        rawKind = null,
        severity = SeverityUi.High,
        title = "Card $index",
        identifier = null,
        whyNow = "Because of $index",
        originAgentName = null,
        activityAtEpochMillis = ACTIVITY_AT,
        decideBy = DecideByUi(DecideByKindUi.Today, null),
        inlineActions = listOf(InlineActionUi.Approve, InlineActionUi.Reject),
        open = OpenTargetUi(OpenKindUi.Approval, "ap-$index", null),
        webUrl = null,
        isReadOnly = false,
        audience = null,
        dismissalKey = "attention:scroll-$index",
    )

/**
 * R-008 follow-up, round 2: a fling composes each card as it scrolls in and never recomposes one that stays. That is
 * why the per-card `ActionRow` lists and the TalkBack action lists are not hoisted into `remember`: a card that is
 * composed once gains nothing from it (the cost per new card is measured in [InboxCardCostTest]).
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class InboxScrollCompositionTest {
    @get:Rule
    val rule = createComposeRule()

    private val inboxCardRuns = AtomicInteger()

    private val tracer =
        object : CompositionTracer {
            override fun traceEventStart(
                key: Int,
                dirty1: Int,
                dirty2: Int,
                info: String,
            ) {
                if (".InboxCard (" in info) inboxCardRuns.incrementAndGet()
            }

            override fun traceEventEnd() = Unit

            override fun isTraceInProgress() = true
        }

    @After
    fun tearDown() {
        Composer.setTracer(NoTracer)
    }

    @Test
    fun scrollingTheWholeListComposesEachCardAboutOnceAndNeverRecomposesAStayingOne() {
        val state =
            InboxState(
                phase = InboxPhase.Content,
                sections = listOf(InboxSectionUi(InboxGroupUi.DecideNow, (0 until CARDS).map(::cardAt))),
                badgeCount = CARDS,
                totalCount = CARDS,
                updatedAtEpochMillis = ACTIVITY_AT,
            )
        Composer.setTracer(tracer)
        rule.setContent { PaperclipTheme(darkTheme = false) { ProvideNow { InboxContent(state, {}, {}) } } }
        rule.waitForIdle()
        inboxCardRuns.get() shouldBeGreaterThan 0

        for (index in 0 until CARDS) {
            rule.onNodeWithTag("inbox-list").performScrollToIndex(index + LEADING_ITEMS)
            rule.waitForIdle()
        }
        for (index in CARDS - 1 downTo 0) {
            rule.onNodeWithTag("inbox-list").performScrollToIndex(index + LEADING_ITEMS)
            rule.waitForIdle()
        }

        // Down once and back once: each card at most twice (it leaves the window and comes back), not once per frame.
        inboxCardRuns.get() shouldBeLessThanOrEqual CARDS * 2 + 4
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
