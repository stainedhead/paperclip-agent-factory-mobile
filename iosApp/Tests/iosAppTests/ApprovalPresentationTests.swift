import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// The words and figures of the Approval screen: composed from the shared UI model, nothing decided here.
@MainActor
struct ApprovalPresentationTests {
    private let enUS = Locale(identifier: "en_US")

    @Test func wholeAmountsDropTheCentsAndOtherAmountsKeepThem() {
        #expect(MoneyFormat.text(MoneyUi(minorUnits: 15_000, currency: "USD"), locale: enUS) == "$150")
        #expect(MoneyFormat.text(MoneyUi(minorUnits: 15_050, currency: "USD"), locale: enUS) == "$150.50")
        #expect(MoneyFormat.text(MoneyUi(minorUnits: 5, currency: "USD"), locale: enUS) == "$0.05")
        #expect(MoneyFormat.text(MoneyUi(minorUnits: 0, currency: "USD"), locale: enUS) == "$0")
    }

    @Test func aTypedAmountParsesToMinorUnitsAndRejectsNonsense() {
        #expect(MoneyFormat.minorUnits(from: "250", locale: enUS) == 25_000)
        #expect(MoneyFormat.minorUnits(from: "250.50", locale: enUS) == 25_050)
        #expect(MoneyFormat.minorUnits(from: "$1,200", locale: enUS) == 120_000)
        #expect(MoneyFormat.minorUnits(from: "", locale: enUS) == nil)
        #expect(MoneyFormat.minorUnits(from: "abc", locale: enUS) == nil)
        #expect(MoneyFormat.minorUnits(from: "-5", locale: enUS) == nil)
    }

    @Test func knownRolesReadAsTheirAcronymsAndOthersAreCapitalised() {
        #expect(ApprovalPresentation.roleName("qa") == "QA")
        #expect(ApprovalPresentation.roleName("cto") == "CTO")
        #expect(ApprovalPresentation.roleName("devops") == "DevOps")
        #expect(ApprovalPresentation.roleName("product_designer") == "Product designer")
        #expect(ApprovalPresentation.roleName(nil) == nil)
    }

    @Test func theTitleComesFromThePayload() {
        let hire = ApprovalPayloadUiHireAgent(
            name: "QA engineer", role: "qa", title: nil, adapterType: nil, reportsToAgentId: nil, reportsToName: nil,
            monthlyBudget: nil, skills: [], capabilities: nil)
        #expect(ApprovalPresentation.title(payload: hire, type: .hireAgent) == "Hire QA engineer")
        let strategy = ApprovalPayloadUiCeoStrategy(title: "Q1 strategy", plan: "Focus")
        #expect(ApprovalPresentation.title(payload: strategy, type: .approveCeoStrategy) == "Q1 strategy")
        #expect(ApprovalPresentation.title(payload: nil, type: .hireAgent) == L10n.text(ApprovalTypeUi.hireAgent))
    }

    @Test func eachStatusReadsInWordsAndMapsToATone() {
        #expect(L10n.text(ApprovalStatusUi.pending) == "Pending")
        #expect(L10n.text(ApprovalStatusUi.revisionRequested) == "Revision requested")
        #expect(ApprovalPresentation.tone(.approved) == .success)
        #expect(ApprovalPresentation.tone(.rejected) == .critical)
        #expect(ApprovalPresentation.tone(.pending) == .warn)
        #expect(ApprovalPresentation.tone(.cancelled) == .neutral)
    }

    @Test func aCommentIsAttributedToAnAgentOrToThePerson() {
        let agent = CommentUi(id: "1", body: "x", createdAtEpochMillis: 0, isFromAgent: true)
        let person = CommentUi(id: "2", body: "x", createdAtEpochMillis: 0, isFromAgent: false)
        #expect(ApprovalPresentation.author(agent) == ApprovalLabel.commentAgent.text)
        #expect(ApprovalPresentation.author(person) == ApprovalLabel.commentYou.text)
    }
}
