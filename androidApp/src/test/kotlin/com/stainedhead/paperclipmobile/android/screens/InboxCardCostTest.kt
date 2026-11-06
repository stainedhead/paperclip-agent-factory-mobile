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
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InlineActionUi
import com.stainedhead.paperclipmobile.presentation.OpenKindUi
import com.stainedhead.paperclipmobile.presentation.OpenTargetUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val ACTIVITY_AT = 1_700_000_000_000L

private fun item(
    kind: SourceKindUi,
    open: OpenTargetUi,
    inline: List<InlineActionUi> = emptyList(),
    webUrl: String? = null,
) = InboxItemUi(
    id = "cost-1",
    kind = kind,
    rawKind = null,
    severity = SeverityUi.High,
    title = "Card",
    identifier = "PAP-1",
    whyNow = "Because",
    originAgentName = "Agent",
    activityAtEpochMillis = ACTIVITY_AT,
    decideBy = DecideByUi(DecideByKindUi.Today, null),
    inlineActions = inline,
    open = open,
    webUrl = webUrl,
    isReadOnly = false,
    audience = null,
    dismissalKey = "attention:cost-1",
)

/**
 * R-008 follow-up: a fling pays for the first composition of every card that scrolls in, not for recomposing the ones
 * on screen. These tests count what one new card composes, so work added to (or taken off) a card shows as a number.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2000dp-xhdpi")
class InboxCardCostTest {
    @get:Rule
    val rule = createComposeRule()

    private val runs = mutableMapOf<String, Int>()

    private val tracer =
        object : CompositionTracer {
            override fun traceEventStart(
                key: Int,
                dirty1: Int,
                dirty2: Int,
                info: String,
            ) {
                val name = info.substringBefore(" (").substringAfterLast('.')
                runs[name] = (runs[name] ?: 0) + 1
            }

            override fun traceEventEnd() = Unit

            override fun isTraceInProgress() = true
        }

    @After
    fun tearDown() {
        Composer.setTracer(NoTracer)
    }

    private fun compose(card: InboxItemUi) {
        val actions = InboxCardActions({}, {}, {}, {}, {}, {})
        Composer.setTracer(tracer)
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ProvideNow { InboxCard(item = card, isDisabled = false, actions = actions) }
            }
        }
        rule.waitForIdle()
        Composer.setTracer(NoTracer)
    }

    private val approval =
        item(
            SourceKindUi.Approval,
            OpenTargetUi(OpenKindUi.Approval, "ap-1", null),
            listOf(InlineActionUi.Approve, InlineActionUi.Reject),
        )

    @Test
    fun aCardThatIsNotAWebDoorBuildsNoWebDoor() {
        compose(approval)
        (runs["rememberWebDoor"] ?: 0) shouldBe 0
    }

    @Test
    fun aWebOnlyCardStillBuildsItsWebDoor() {
        compose(
            item(
                SourceKindUi.Unsupported,
                OpenTargetUi(OpenKindUi.WebOnly, "w-1", null),
                webUrl = "https://example.com/x",
            ),
        )
        (runs["rememberWebDoor"] ?: 0) shouldBe 1
        (runs["OpenInWebButton"] ?: 0) shouldBe 1
    }

    @Test
    fun aCardButtonIsNotAMaterialSurface() {
        compose(approval)
        // A Material Button is a Surface with its own layer, elevation animation and content-colour provider: three of
        // them per card made up most of what a card composed. The look is the design system's, drawn directly.
        (runs["Surface"] ?: 0) shouldBe 0
        (runs["Button"] ?: 0) shouldBe 0
        (runs["AppButton"] ?: 0) shouldBe 3
    }

    @Test
    fun anApprovalCardComposesWithinItsBudget() {
        compose(approval)
        runs.values.sum() shouldBeLessThanOrEqual APPROVAL_COMPOSABLES_BUDGET
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

    private companion object {
        /** Composables one approval card runs on first composition, with the theme around it: 210 before, 133 now. */
        const val APPROVAL_COMPOSABLES_BUDGET = 133
    }
}
