import PaperclipKit
import Testing

@testable import iosApp

/// The words of the Decision screen: composed from the shared UI model, nothing decided here.
@MainActor
struct DecisionPresentationTests {
    @Test func anEffectReadsAsItsCatalogSentenceWithItsDetail() {
        let status = EffectSummaryUi(
            kind: .updateIssueStatus, targetIssueId: "x", detail: "in_progress", isStrict: false)
        #expect(DecisionPresentation.effectLine(status) == "Sets the issue status to in progress")
        let assign = EffectSummaryUi(kind: .assignIssue, targetIssueId: "x", detail: "DevOps", isStrict: false)
        #expect(DecisionPresentation.effectLine(assign) == "Assigns the issue to DevOps")
        let create = EffectSummaryUi(kind: .createIssue, targetIssueId: "x", detail: "QA regression", isStrict: false)
        #expect(DecisionPresentation.effectLine(create).contains("QA regression"))
    }

    @Test func anEffectWithoutADetailDoesNotShowAnEmptyPlaceholder() {
        let assign = EffectSummaryUi(kind: .assignIssue, targetIssueId: "x", detail: nil, isStrict: false)
        #expect(!DecisionPresentation.effectLine(assign).contains("%"))
    }

    @Test func aResultRowNamesTheEffectAndItsStatus() {
        let result = EffectResultUi(index: 1, effectType: "update_issue_status", status: .executed, reason: nil)
        #expect(DecisionPresentation.resultTitle(result) == "Update issue status")
        #expect(L10n.text(EffectStatusUi.executed) == "Done")
        #expect(L10n.text(EffectStatusUi.skipped) == "Skipped")
        #expect(L10n.text(EffectStatusUi.failed) == "Failed")
    }

    @Test func aSkippedReasonIsExplainedAndAnUnknownReasonIsShownAsTheServerSaidIt() {
        #expect(DecisionPresentation.reasonText("target_changed") == "The issue changed after this was proposed.")
        #expect(DecisionPresentation.reasonText("weird_thing") == "weird thing")
        #expect(DecisionPresentation.reasonText(nil) == nil)
    }

    @Test func decideByReadsInWords() {
        #expect(DecisionPresentation.decideByText(DecideByUi(kind: .today, isoDate: nil)) == "Decide by today")
        #expect(DecisionPresentation.decideByText(DecideByUi(kind: .thisWeek, isoDate: nil)) == "Decide this week")
        #expect(DecisionPresentation.decideByText(DecideByUi(kind: .none, isoDate: nil)) == nil)
        #expect(
            DecisionPresentation.decideByText(DecideByUi(kind: .onDate, isoDate: "2026-11-05"))?.hasPrefix("Decide by ")
                == true)
    }

    @Test func eachFinalKindHasItsSentenceAndTheChosenOptionIsNamed() {
        let decided = DecisionFinalUi(kind: .decided, chosenOptionLabel: "Wait for QA sign-off")
        #expect(DecisionPresentation.finalText(decided) == "Already decided: Wait for QA sign-off")
        #expect(
            DecisionPresentation.finalText(DecisionFinalUi(kind: .decided, chosenOptionLabel: nil))
                == "This decision was already made.")
        #expect(
            DecisionPresentation.finalText(DecisionFinalUi(kind: .expired, chosenOptionLabel: nil)).contains("expired"))
        #expect(
            DecisionPresentation.finalText(DecisionFinalUi(kind: .dismissed, chosenOptionLabel: nil)).contains(
                "dismissed"))
        #expect(
            DecisionPresentation.finalText(DecisionFinalUi(kind: .cancelled, chosenOptionLabel: nil)).contains(
                "cancelled"))
    }

    @Test func theRequiredInputCounterShowsTheLimit() {
        let input = InputUi(id: "reason", label: "Why", placeholder: nil, isRequired: true, maxLength: 40, value: "abc")
        #expect(DecisionPresentation.counter(input) == "3 / 40")
        let unlimited = InputUi(id: "n", label: "N", placeholder: nil, isRequired: false, maxLength: nil, value: "abc")
        #expect(DecisionPresentation.counter(unlimited) == nil)
    }

    @Test func anOptionIsSpokenWithItsEffectsAndWhetherItAsksFaceID() {
        let option = OptionUi(
            id: "cancel", label: "Cancel the migration", summary: nil, isDestructive: true, needsDeviceAuth: true,
            effects: [EffectSummaryUi(kind: .cancelIssueTree, targetIssueId: "x", detail: "Cancelled", isStrict: true)])
        let spoken = DecisionPresentation.optionLabel(option)
        #expect(spoken.contains("Cancel the migration"))
        #expect(spoken.contains("Face ID"))
        #expect(spoken.contains("Cancels the issue"))
    }
}
