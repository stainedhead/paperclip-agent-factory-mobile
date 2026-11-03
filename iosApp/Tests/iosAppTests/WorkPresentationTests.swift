import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// The words and colours of the Work list and Issue screens: lookups over the shared state, nothing decided here.
@MainActor
struct WorkPresentationTests {
    private func row(
        status: IssueStatusUi = .blocked, priority: IssuePriorityUi = .high, assignee: String? = "Engineer 1",
        mine: Bool = false
    ) -> IssueRowUi {
        IssueRowUi(
            id: "pap-1204", identifier: "PAP-1204", title: "Add retries to webhook ingest", status: status,
            priority: priority, assigneeName: assignee, assignedToMe: mine, updatedAtEpochMillis: 0)
    }

    private func comment(kind: CommentAuthorUi, name: String?, mine: Bool, ageSeconds: Int64) -> IssueCommentUi {
        IssueCommentUi(
            id: "c", body: "Hello", authorKind: kind, authorName: name, isMine: mine,
            createdAtEpochMillis: 1_000_000 - ageSeconds * 1000)
    }

    @Test func aRowReadsAsNumberTitleStatusPriorityAndAssignee() {
        let presentation = IssueRowPresentation(row: row())
        #expect(presentation.summary == "Blocked \u{00B7} High \u{00B7} Engineer 1")
        #expect(
            presentation.accessibility
                == "PAP-1204. Add retries to webhook ingest. Blocked \u{00B7} High \u{00B7} Engineer 1")
    }

    @Test func aRowAssignedToThePersonSaysYouAndAnUnassignedOneSaysUnassigned() {
        #expect(IssueRowPresentation(row: row(assignee: nil, mine: true)).assignee == "You")
        #expect(IssueRowPresentation(row: row(assignee: nil)).assignee == "Unassigned")
    }

    @Test func blockedIsCriticalDoneIsSuccessAndHighPriorityIsAWarning() {
        #expect(IssueTone.status(.blocked) == .critical)
        #expect(IssueTone.status(.done) == .success)
        #expect(IssueTone.status(.inReview) == .warn)
        #expect(IssueTone.status(.todo) == .neutral)
        #expect(IssueTone.priority(.critical) == .critical)
        #expect(IssueTone.priority(.high) == .warn)
        #expect(IssueTone.priority(.low) == .neutral)
    }

    @Test func theChipsFollowTheSavedFilterOrderOfTheBrief() {
        #expect(IssueFilterOrder.chips == [.allOpen, .blocked, .criticalAndHigh, .inReview, .assignedToMe])
        #expect(Set(IssueFilterOrder.chips) == Set(IssueFilterUi.allCases))
    }

    @Test func anAgentCommentNamesTheAgentThenAgentThenItsAge() {
        let reference = Date(timeIntervalSince1970: 1000)
        let line = CommentPresentation.authorLine(
            comment(kind: .agent, name: "Engineer 1", mine: false, ageSeconds: 25 * 60), referenceDate: reference)
        #expect(line == "Engineer 1 \u{00B7} agent \u{00B7} 25m")
    }

    @Test func myCommentSaysYouAndAnotherPersonsCommentKeepsTheirName() {
        let reference = Date(timeIntervalSince1970: 1000)
        #expect(
            CommentPresentation.authorLine(
                comment(kind: .user, name: nil, mine: true, ageSeconds: 4 * 3600), referenceDate: reference)
                == "You \u{00B7} 4h")
        #expect(
            CommentPresentation.authorLine(
                comment(kind: .user, name: "Sam", mine: false, ageSeconds: 30), referenceDate: reference)
                == "Sam \u{00B7} 30s")
    }

    @Test func aCommentFromAnUnnamedAgentStillSaysAgent() {
        let reference = Date(timeIntervalSince1970: 1000)
        let line = CommentPresentation.authorLine(
            comment(kind: .agent, name: nil, mine: false, ageSeconds: 60), referenceDate: reference)
        #expect(line == "Agent \u{00B7} agent \u{00B7} 1m")
    }

    @Test func fileSizesAreWrittenForPeople() {
        #expect(FileSizeText.text(bytes: 4096) == "4 KB")
        #expect(FileSizeText.text(bytes: 0).contains("0"))
    }

    @Test func markdownKeepsInlineStylingAndPlainTextAsItIs() {
        #expect(String(MarkdownText.attributed("**Bold** and plain").characters) == "Bold and plain")
        #expect(String(MarkdownText.attributed("1. Add\n2. Back off").characters) == "1. Add\n2. Back off")
    }
}
