package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecideByUi
import com.stainedhead.paperclipmobile.presentation.InboxGroupUi
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InboxSectionUi
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.presentation.InlineActionUi
import com.stainedhead.paperclipmobile.presentation.OpenKindUi
import com.stainedhead.paperclipmobile.presentation.OpenTargetUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.SnoozePresetUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW_MILLIS = 1_800_000_000_000L
private const val FIVE_MINUTES = 300_000L

private fun card(
    id: String,
    kind: SourceKindUi,
    open: OpenTargetUi,
    inline: List<InlineActionUi> = emptyList(),
) = InboxItemUi(
    id = id,
    kind = kind,
    rawKind = null,
    severity = SeverityUi.High,
    title = "Card $id",
    identifier = null,
    whyNow = "Because of $id",
    originAgentName = null,
    activityAtEpochMillis = NOW_MILLIS - FIVE_MINUTES,
    decideBy = DecideByUi(DecideByKindUi.Today, null),
    inlineActions = inline,
    open = open,
    webUrl = null,
    isReadOnly = false,
    audience = null,
    dismissalKey = "attention:$id",
)

private fun contentState(
    items: List<InboxItemUi>,
    disabled: ActionsDisabledReason? = null,
    role: RoleUi? = RoleUi.Owner,
) = InboxState(
    phase = InboxPhase.Content,
    sections = listOf(InboxSectionUi(InboxGroupUi.DecideNow, items)),
    badgeCount = items.size,
    totalCount = items.size,
    updatedAtEpochMillis = NOW_MILLIS,
    role = role,
    actionsDisabledReason = disabled,
    snoozeOptions = listOf(SnoozePresetUi.OneHour),
)

/** AND-030 variants the demo graph cannot produce: a viewer, an unknown card kind, pull to refresh. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2000dp-xhdpi")
class InboxVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val intents = mutableListOf<InboxIntent>()
    private val opened = mutableListOf<InboxItemUi>()

    @After
    fun tearDown() = app.close()

    private var launcher = RecordingLauncher()

    private fun show(state: InboxState) {
        rule.setContent {
            CompositionLocalProvider(LocalWebOpener provides WebOpener(launcher)) {
                app.Themed { InboxContent(state, { intents += it }, { opened += it }) }
            }
        }
        rule.waitForIdle()
    }

    private val approval =
        card(
            "approval:ap-1",
            SourceKindUi.Approval,
            OpenTargetUi(OpenKindUi.Approval, "ap-1", null),
            listOf(InlineActionUi.Approve, InlineActionUi.Reject),
        )

    @Test
    fun aViewerSeesTheCardsWithoutAnyActionAndWithTheReason() {
        val viewerView = approval.copy(inlineActions = emptyList(), isReadOnly = true)

        show(contentState(listOf(viewerView), ActionsDisabledReason.ViewerRole, RoleUi.Viewer))

        rule.onNodeWithTag("inbox-card-${approval.id}").assertIsDisplayed()
        rule.hasTag("inbox-approve-${approval.id}") shouldBe false
        rule.hasTag("inbox-reject-${approval.id}") shouldBe false
        rule.onNodeWithTag("inbox-details-${approval.id}").assertIsDisplayed()
        rule.onNodeWithText("Your role is Viewer, so you can look but not change anything.").assertIsDisplayed()
        val open =
            rule.onNode(hasTestTag("attention-card-open") and hasAnyAncestor(hasTestTag("inbox-card-${approval.id}")))
        open.fetchSemanticsNode().config[SemanticsActions.CustomActions].size shouldBe 0
    }

    private fun webCard(url: String?) =
        card("future:x", SourceKindUi.Unsupported, OpenTargetUi(OpenKindUi.WebOnly, "x", null))
            .copy(rawKind = "future_kind", webUrl = url)

    @Test
    fun anUnknownKindOffersOnlyTheWebAppAndOpensItsAddressThroughTheWebOpener() {
        val unknown = webCard("https://paperclip.example/x")

        show(contentState(listOf(unknown)))

        rule.onNodeWithText("· future_kind").assertIsDisplayed()
        rule.onNodeWithTag("inbox-web-${unknown.id}").performClick()
        launcher.tabbed shouldBe listOf("https://paperclip.example/x")
        opened shouldBe emptyList()
        rule.hasTag("inbox-web-failed-${unknown.id}") shouldBe false
    }

    @Test
    fun aPhoneThatCannotShowThePageSaysSoOnTheCard() {
        launcher = RecordingLauncher(tabs = false, browser = false)
        val unknown = webCard("https://paperclip.example/x")

        show(contentState(listOf(unknown)))
        rule.onNodeWithTag("inbox-web-${unknown.id}").performClick()

        rule.onNodeWithTag("inbox-web-failed-${unknown.id}").assertIsDisplayed()
    }

    @Test
    fun aWebCardWithoutAUsableHttpsAddressHasNoWebButton() {
        val none = webCard(null).copy(id = "future:none")
        val insecure = webCard("http://paperclip.example/x").copy(id = "future:http")

        show(contentState(listOf(none, insecure)))

        rule.onNodeWithTag("inbox-card-${none.id}").assertIsDisplayed()
        rule.hasTag("inbox-web-${none.id}") shouldBe false
        rule.hasTag("inbox-web-${insecure.id}") shouldBe false
    }

    @Test
    fun pullingTheListDownAsksForARefresh() {
        show(contentState(listOf(approval)))

        rule.onNodeWithTag("inbox-list").performTouchInput { swipeDown() }

        rule.waitUntil(WAIT_MILLIS) { InboxIntent.Refresh in intents }
        intents shouldContain InboxIntent.Refresh
    }

    @Test
    fun withNothingToDecideTheInboxSaysSo() {
        show(InboxState(phase = InboxPhase.Empty, updatedAtEpochMillis = NOW_MILLIS))

        rule.onNodeWithText("Nothing needs you").assertIsDisplayed()
        rule.onNodeWithTag("inbox-empty").assertIsDisplayed()
    }

    @Test
    fun offlineWithNothingSavedExplainsInsteadOfShowingABlankScreen() {
        show(InboxState(phase = InboxPhase.OfflineEmpty, isOffline = true))

        rule.onNodeWithText("You're offline").assertIsDisplayed()
        rule.onNodeWithTag("inbox-offline-empty").assertIsDisplayed()
    }
}
