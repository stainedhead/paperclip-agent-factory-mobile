package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.AssigneeKindUi
import com.stainedhead.paperclipmobile.presentation.CommentAuthorUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InteractionStatusUi
import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import com.stainedhead.paperclipmobile.presentation.ThreadItemKind
import com.stainedhead.paperclipmobile.presentation.WorkListIntent
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(60.seconds) { first(predicate) } }

private const val ALL_OPEN_ROWS = 9
private const val HOME = "demo-home"
private const val ACME = "company-acme"

/** Work list and Issue on the demo graph behave like a server for the screens that link to them (UI-050). */
class DemoWorkFlowTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `the thread names the agent who wrote a comment and orders a new comment last`() =
        runTest {
            val issue = graph.work.createIssueStateHolder(HOME, ACME, DemoIds.ISSUE_RETRIES)
            val first = issue.state.await { it.phase == DetailPhase.Content }
            first.thread
                .mapNotNull { it.comment }
                .first()
                .authorName shouldBe "Engineer 1"

            issue.onIntent(
                IssueIntent.CommentSubmitted("Go with exponential backoff.", reopen = false, interrupt = false),
            )
            val after = issue.state.await { s -> s.thread.any { it.comment?.body == "Go with exponential backoff." } }

            val last = after.thread.last()
            last.kind shouldBe ThreadItemKind.Comment
            last.comment?.authorKind shouldBe CommentAuthorUi.User
            last.comment?.isMine shouldBe true
            issue.close()
        }

    @Test
    fun `answering the two questions marks the card answered and clears the Inbox question`() =
        runTest {
            val inbox = graph.attention.createInboxStateHolder(HOME, ACME)
            val before = inbox.state.await { it.phase == InboxPhase.Content }
            before.sections.flatMap { it.items }.any { it.id.endsWith("int-1204-questions") } shouldBe true
            val issue = graph.work.createIssueStateHolder(HOME, ACME, DemoIds.ISSUE_RETRIES)
            val card =
                issue.state
                    .await { it.phase == DetailPhase.Content }
                    .thread
                    .mapNotNull { it.interaction }
                    .single()

            val q1 = card.questions[0]
            val q2 = card.questions[1]
            issue.onIntent(IssueIntent.InteractionChoiceToggled(card.id, q1.id, q1.options[0].id))
            issue.onIntent(IssueIntent.InteractionChoiceToggled(card.id, q2.id, q2.options[1].id))
            issue.state.await { s ->
                s.thread
                    .mapNotNull { it.interaction }
                    .single()
                    .submitIssue == null
            }
            issue.onIntent(IssueIntent.InteractionPrimaryTapped(card.id))
            val answered =
                issue.state.await { s ->
                    s.thread
                        .mapNotNull { it.interaction }
                        .single()
                        .status ==
                        InteractionStatusUi.Answered
                }
            answered.thread
                .mapNotNull { it.interaction }
                .single()
                .canAnswer shouldBe false

            inbox.onIntent(InboxIntent.Refresh)
            val after =
                inbox.state.await { s ->
                    s.sections.flatMap { it.items }.none { it.id.endsWith("int-1204-questions") }
                }
            // The badge counts what is due today; the question is due this week, so it stays.
            after.badgeCount shouldBe before.badgeCount
            issue.close()
            inbox.close()
        }

    @Test
    fun `status and assignee changes show on the issue and in the Work list`() =
        runTest {
            val issue = graph.work.createIssueStateHolder(HOME, ACME, DemoIds.ISSUE_RETRIES)
            issue.state.await { it.phase == DetailPhase.Content }
            issue.onIntent(IssueIntent.StatusSelected(IssueStatusUi.InProgress))
            issue.state.await { it.header?.status == IssueStatusUi.InProgress && !it.isBusy }
            issue.onIntent(IssueIntent.AssigneeSelected(AssigneeKindUi.Agent, DemoIds.AGENT_CTO))

            val state = issue.state.await { it.header?.assigneeName == "CTO" && !it.isBusy }
            state.header?.priority shouldBe IssuePriorityUi.High

            val list = graph.work.createWorkListStateHolder(HOME, ACME)
            list.onIntent(WorkListIntent.FilterSelected(IssueFilterUi.Blocked))
            // The chip changes before the rows do, so wait for the rows of the new filter.
            val blocked =
                list.state.await {
                    it.phase == DetailPhase.Content && it.filter == IssueFilterUi.Blocked &&
                        it.rows.size < ALL_OPEN_ROWS
                }
            blocked.rows.map { it.identifier }.shouldContainExactly(emptyList())
            issue.close()
            list.close()
        }

    @Test
    fun `creating an issue from the Work list opens a readable issue`() =
        runTest {
            val list = graph.work.createWorkListStateHolder(HOME, ACME)
            list.state.await { it.phase == DetailPhase.Content && it.canCreate }

            list.onIntent(WorkListIntent.CreateSubmitted("Rotate the webhook secret", IssuePriorityUi.High))
            val created = list.state.await { it.createdIssueId != null }

            val issue = graph.work.createIssueStateHolder(HOME, ACME, created.createdIssueId!!)
            val state = issue.state.await { it.phase == DetailPhase.Content }
            state.header?.title shouldBe "Rotate the webhook secret"
            state.header?.priority shouldBe IssuePriorityUi.High
            issue.close()
            list.close()
        }
}
