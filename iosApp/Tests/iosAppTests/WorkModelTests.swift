import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// The Work list and the Issue screen through their models on the demo graph. The models mirror the shared state and
/// forward intents; the filters, paging, role gates, thread order and the cards' validation are shared.
@MainActor
struct WorkModelTests {
    private let home = "demo-home"
    private let acme = "company-acme"
    private let retries = "pap-1204"

    private func graph() -> PaperclipGraph { PaperclipCore.shared.createDemoGraph() }

    private func list(_ graph: PaperclipGraph) -> WorkListModel {
        WorkListModel(holder: graph.work.createWorkListStateHolder(instanceId: home, companyId: acme))
    }

    private func issue(_ graph: PaperclipGraph, _ id: String? = nil) -> IssueScreenModel {
        IssueScreenModel(
            holder: graph.work.createIssueStateHolder(instanceId: home, companyId: acme, issueId: id ?? retries))
    }

    // MARK: Work list

    @Test func theListShowsEveryOpenIssueWithTheBlockedOneAsInTheMock() async {
        let model = list(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content && !model.state.rows.isEmpty })
        let retries = model.state.rows.first { $0.identifier == "PAP-1204" }
        #expect(retries?.status == .blocked && retries?.priority == .high && retries?.assigneeName == "Engineer 1")
        #expect(model.state.filter == .allOpen)
        #expect(model.state.canCreate)
        observing.cancel()
        model.close()
    }

    @Test func aFilterChipChangesTheRowsAndSearchNarrowsThem() async {
        let model = list(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content && !model.state.rows.isEmpty })
        model.select(.blocked)
        #expect(
            await eventually { model.state.filter == .blocked && model.state.rows.map(\.identifier) == ["PAP-1204"] })
        model.select(.allOpen)
        #expect(await eventually { model.state.filter == .allOpen && model.state.rows.count > 1 })
        model.search("refund")
        #expect(await eventually { model.state.rows.map(\.identifier) == ["PAP-1190"] })
        model.search("")
        #expect(await eventually { model.state.rows.count > 1 })
        observing.cancel()
        model.close()
    }

    @Test func creatingAnIssueGivesItsIdOnceAndAcknowledgingClearsIt() async {
        let model = list(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.canCreate })
        model.create(title: "Rotate the webhook secret", priority: .high)
        #expect(await eventually { model.state.createdIssueId != nil })
        model.createdAcknowledged()
        #expect(await eventually { model.state.createdIssueId == nil })
        observing.cancel()
        model.close()
    }

    @Test func aBlankTitleIsRefusedByTheSharedCoreNotBySwift() async {
        let model = list(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.canCreate })
        model.create(title: "   ", priority: .medium)
        #expect(await eventually { model.state.feedback?.invalid == .titleRequired })
        #expect(model.state.createdIssueId == nil)
        observing.cancel()
        model.close()
    }

    // MARK: Issue

    @Test func theIssueCarriesTheMocksHeaderThreadAndQuestions() async {
        let model = issue(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        let header = model.state.header
        #expect(header?.identifier == "PAP-1204" && header?.status == .blocked && header?.priority == .high)
        #expect(header?.assigneeName == "Engineer 1")
        #expect(model.state.thread.contains { $0.comment?.authorName == "Engineer 1" })
        let card = model.state.thread.compactMap(\.interaction).first
        #expect(card?.kind == .askUserQuestions && card?.questions.count == 2 && card?.canAnswer == true)
        observing.cancel()
        model.close()
    }

    @Test func theLazyTabsLoadOnSelectionAndShowTheirCounts() async {
        let model = issue(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(IssueTabPresentation.title(.runs, state: model.state) == "Runs")
        model.select(.runs)
        model.select(.files)
        #expect(await eventually { model.state.runsLoaded && model.state.filesLoaded })
        #expect(IssueTabPresentation.title(.runs, state: model.state) == "Runs 3")
        #expect(IssueTabPresentation.title(.files, state: model.state) == "Files 2")
        observing.cancel()
        model.close()
    }

    @Test func aCommentIsAddedLastAndAnEmptyOneIsRefusedBySharedCode() async {
        let model = issue(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.canComment })
        model.comment("  ", reopen: false, interrupt: false)
        #expect(await eventually { model.state.feedback?.invalid == .bodyRequired })
        model.dismissMessage()
        model.comment("Use exponential backoff.", reopen: false, interrupt: false)
        #expect(await eventually { model.state.thread.last?.comment?.body == "Use exponential backoff." })
        #expect(model.state.thread.last?.comment?.isMine == true)
        observing.cancel()
        model.close()
    }

    @Test func statusAndAssigneeChangesShowOnTheHeaderOneAtATime() async {
        let model = issue(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.canChangeStatus })
        #expect(!model.state.statusOptions.contains(.blocked), "the current status is not offered again")
        model.setStatus(.inProgress)
        #expect(await eventually { model.state.header?.status == .inProgress && !model.state.isBusy })
        model.assign(.agent, id: "agent-cto")
        #expect(await eventually { model.state.header?.assigneeName == "CTO" && !model.state.isBusy })
        model.assign(.me, id: nil)
        #expect(await eventually { model.state.header?.assigneeIsMe == true && !model.state.isBusy })
        observing.cancel()
        model.close()
    }

    @Test func theQuestionsNeedBothAnswersAndSendingMarksTheCardAnswered() async {
        let model = issue(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.thread.contains { $0.interaction != nil } })
        let card = model.state.thread.compactMap(\.interaction).first
        let (first, second) = (card?.questions[0], card?.questions[1])
        #expect(card?.submitIssue != nil, "nothing is answered yet")
        model.toggle(card?.id ?? "", group: first?.id ?? "", option: first?.options[0].id ?? "")
        model.toggle(card?.id ?? "", group: second?.id ?? "", option: second?.options[1].id ?? "")
        #expect(await eventually { model.state.thread.compactMap(\.interaction).first?.submitIssue == nil })
        model.primary(card?.id ?? "")
        #expect(await eventually { model.state.thread.compactMap(\.interaction).first?.status == .answered })
        #expect(model.state.thread.compactMap(\.interaction).first?.canAnswer == false)
        observing.cancel()
        model.close()
    }

    @Test func typedAnswerTextIsKeptInTheCard() async {
        let model = issue(graph())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.thread.contains { $0.interaction != nil } })
        let card = model.state.thread.compactMap(\.interaction).first
        let second = card?.questions[1]
        model.text(card?.id ?? "", target: second?.id ?? "", "7")
        #expect(await eventually { model.state.thread.compactMap(\.interaction).first?.questions[1].otherText == "7" })
        observing.cancel()
        model.close()
    }

    @Test func aSubtaskIsAddedUnderTheIssue() async {
        let model = issue(graph(), "pap-1182")
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.canCreateSubtask })
        let before = model.state.subtasks.count
        model.addSubtask("Write the migration guide")
        #expect(await eventually { model.state.subtasks.count == before + 1 })
        observing.cancel()
        model.close()
    }
}
