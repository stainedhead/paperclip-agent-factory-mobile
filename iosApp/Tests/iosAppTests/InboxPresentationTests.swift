import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// What a card says and where it goes: pure lookups over the shared UI model, tested on the demo data.
@MainActor
struct InboxPresentationTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    private func items() async -> [InboxItemUi] {
        let holder = graph.attention.createInboxStateHolder(instanceId: "demo-home", companyId: "company-acme")
        defer { holder.close() }
        _ = await eventually { holder.state.value.phase == .content }
        return holder.state.value.sections.flatMap(\.items)
    }

    private func item(_ id: String, in all: [InboxItemUi]) -> InboxItemUi {
        guard let found = all.first(where: { $0.id == id }) else { fatalError("no demo card \(id)") }
        return found
    }

    private var reference: Date { Date(timeIntervalSince1970: Double(graph.nowEpochMillis()) / 1000) }

    @Test func theDecisionCardReadsLikeTheMock() async {
        let all = await items()
        let card = InboxCardPresentation(item: item("decision:dec-1182", in: all), referenceDate: reference)
        #expect(card.kindLine == "Decision \u{00B7} PAP-1182")
        #expect(card.age == "8m")
        #expect(card.title == "Ship the billing migration to production?")
        #expect(card.context == "CTO proposes 3 options, PAP-1182, blocks 4 tasks")
        #expect(card.severity == .critical)
    }

    @Test func theOriginAgentIsAddedOnlyWhenTheContextDoesNotNameIt() async {
        let all = await items()
        let approval = InboxCardPresentation(item: item("approval:ap-hire-qa", in: all), referenceDate: reference)
        #expect(approval.context == "Requested by CEO, reports to CTO, $150/mo")
        let question = InboxCardPresentation(
            item: item("issue_thread_interaction:int-1204-questions", in: all), referenceDate: reference)
        #expect(question.kindLine == "Agent question \u{00B7} PAP-1204")
        #expect(question.context == "Agent question, PAP-1204 \u{00B7} Engineer 1")
    }

    @Test func theCombinedAccessibilityLabelSaysSeverityKindTitleContextAndAge() async {
        let all = await items()
        let card = InboxCardPresentation(item: item("decision:dec-1182", in: all), referenceDate: reference)
        let spoken =
            "Critical. Decision \u{00B7} PAP-1182. Ship the billing migration to production?. "
            + "CTO proposes 3 options, PAP-1182, blocks 4 tasks. 8 minutes ago"
        #expect(card.accessibilityLabel == spoken)
    }

    @Test func aCardWithoutATitleFallsBackToItsKind() {
        let card = InboxCardPresentation(
            kind: .unsupported, rawKind: "future_kind", title: nil, identifier: nil, severity: .low,
            whyNow: "Something new", origin: nil, activityAtEpochMillis: 0,
            referenceDate: Date(timeIntervalSince1970: 120))
        #expect(card.title == "future_kind")
        #expect(card.kindLine == "future_kind")
        #expect(card.age == "2m")
    }

    @Test func cardsOpenTheirDetailScreensAndTheBudgetSheet() async {
        let all = await items()
        func destination(_ id: String) -> InboxDestination {
            let open = item(id, in: all)
            return InboxDestination(target: open.open, webUrl: open.webUrl)
        }
        #expect(destination("decision:dec-1182") == .route(.decision(id: "dec-1182")))
        #expect(destination("approval:ap-hire-qa") == .route(.approval(id: "ap-hire-qa")))
        #expect(destination("budget_alert:inc-eng-2") == .sheet(.budgetIncident(id: "inc-eng-2")))
        #expect(destination("failed_run:run-qa-1190") == .route(.run(id: "run-qa-1190")))
        guard case .route(.issue) = destination("issue_thread_interaction:int-1204-questions") else {
            Issue.record("an interaction opens its issue")
            return
        }
    }

    @Test func aWebOnlyCardOpensItsLinkOnlyWhenItIsAWebAddress() {
        let target = OpenTargetUi(kind: .webOnly, id: "x", issueId: nil)
        #expect(
            InboxDestination(target: target, webUrl: "https://home.example.com/PAP/join/1")
                == .web(URL(string: "https://home.example.com/PAP/join/1")!))
        #expect(InboxDestination(target: target, webUrl: nil) == .none)
        #expect(InboxDestination(target: target, webUrl: "javascript:alert(1)") == .none)
        #expect(InboxDestination(target: target, webUrl: "not a url") == .none)
    }

    @Test func aPlainHttpLinkIsNeverOpenedExceptOnTheLocalDevelopmentHosts() {
        let target = OpenTargetUi(kind: .webOnly, id: "x", issueId: nil)
        #expect(InboxDestination(target: target, webUrl: "http://home.example.com/PAP/join/1") == .none)
        #expect(InboxDestination(target: target, webUrl: "http://192.168.1.5:3100/x") == .none)
        #expect(WebAddress.url("https://home.example.com/x") != nil)
        // A Debug build may talk to a local factory over http (AGENTS.md security rules).
        #expect((WebAddress.url("http://localhost:3100/x") != nil) == WebAddress.allowsLocalHttp)
        #expect((WebAddress.url("http://10.0.2.2:3100/x") != nil) == WebAddress.allowsLocalHttp)
    }

    @Test func cardsOfferTheInlineActionsTheStateGivesAndTheNavigationThatFitsTheirKind() async {
        let all = await items()
        let decision = InboxCardActions(item: item("decision:dec-1182", in: all))
        #expect(decision.link == .reviewOptions)
        #expect(decision.inline.isEmpty)
        let approval = InboxCardActions(item: item("approval:ap-hire-qa", in: all))
        #expect(approval.link == .details)
        #expect(approval.offers(.approve) && approval.offers(.reject))
        let budget = InboxCardActions(item: item("budget_alert:inc-eng-2", in: all))
        #expect(budget.offers(.keepPaused) && budget.offers(.raiseAndResume))
        #expect(budget.link == .chevron)
        let question = InboxCardActions(item: item("issue_thread_interaction:int-1204-questions", in: all))
        #expect(question.link == .chevron)
        #expect(question.inline.isEmpty)
        let web = InboxCardActions(
            kind: .unsupported, target: OpenTargetUi(kind: .webOnly, id: "x", issueId: nil), inline: [])
        #expect(web.link == .openInWeb)
    }

    @Test func theChipsAreLabelledByKindWithTheirCount() {
        #expect(SourceKindUi.decision.chipLabel == "Decisions")
        #expect(SourceKindUi.issueThreadInteraction.chipLabel == "Questions")
        #expect(SourceKindUi.approval.cardLabel == "Approval")
        #expect(InboxLabel.chip.text("Approvals", 2) == "Approvals 2")
        #expect(InboxLabel.allCount.text(9) == "All 9")
    }

    @Test func theDueTodayLineSaysHowManyAreNewOrDue() {
        #expect(InboxLabel.dueToday.text(3) == "3 new or due today")
        #expect(InboxLabel.dueTodayNone.text == "Nothing new or due today")
    }

    @Test func theFactoryLabelNamesTheFactoryAndTheSelectedCompany() {
        #expect(
            InboxHeader.factoryLabel(factory: "Home factory", company: "Acme Labs") == "Home factory \u{00B7} Acme Labs"
        )
        #expect(InboxHeader.factoryLabel(factory: "Home factory", company: nil) == "Home factory")
        #expect(InboxHeader.factoryLabel(factory: nil, company: nil) == "")
    }
}
