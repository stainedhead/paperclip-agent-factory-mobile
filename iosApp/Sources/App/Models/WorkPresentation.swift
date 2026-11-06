import Foundation
import PaperclipKit

/// How the Work list and Issue screens word and colour what the shared state says. Nothing here decides what is
/// allowed or offered: that is the state's. These are lookups and joins.
enum IssueTone {
    static func status(_ status: IssueStatusUi) -> StatusTone { StatusTone(status.tone) }

    static func priority(_ priority: IssuePriorityUi) -> StatusTone { StatusTone(priority.tone) }
}

/// The saved filters in the order of the chips.
enum IssueFilterOrder {
    static let chips: [IssueFilterUi] = [.allOpen, .blocked, .criticalAndHigh, .inReview, .assignedToMe]
}

/// One row of the Work list.
@MainActor
struct IssueRowPresentation: Equatable {
    let assignee: String
    let summary: String
    let accessibility: String

    init(row: IssueRowUi) {
        assignee =
            row.assignedToMe ? WorkLabel.assignedToYou.text : (row.assigneeName ?? WorkLabel.unassigned.text)
        summary = [L10n.text(row.status), L10n.text(row.priority), assignee].joined(separator: " \u{00B7} ")
        accessibility = "\(row.identifier). \(row.title). \(summary)"
    }
}

/// The line above a comment: "Engineer 1 · agent · 25m", "You · 4h".
@MainActor
enum CommentPresentation {
    static func authorLine(_ comment: IssueCommentUi, referenceDate: Date?) -> String {
        let seconds = (referenceDate ?? Date()).timeIntervalSince1970 - Double(comment.createdAtEpochMillis) / 1000
        let age = RelativeAge.text(seconds: seconds)
        switch comment.authorKind {
        case .user:
            return comment.isMine
                ? "\(IssueLabel.authorYou.text) \u{00B7} \(age)"
                : [comment.authorName, age].compactMap { $0 }.joined(separator: " \u{00B7} ")
        case .agent:
            return IssueLabel.authorLine.text(
                comment.authorName ?? IssueLabel.authorAgent.text.capitalized,
                IssueLabel.authorAgent.text, age)
        case .system:
            return "\(IssueLabel.authorSystem.text) \u{00B7} \(age)"
        }
    }
}

/// The text of a tab button: its name and, once the tab has been read, how many it holds ("Runs 3").
@MainActor
enum IssueTabPresentation {
    static func title(_ tab: IssueTab, state: IssueState) -> String {
        switch tab {
        case .runs where state.runsLoaded: IssueLabel.tabWithCount.text(L10n.text(tab), state.runs.count)
        case .files where state.filesLoaded: IssueLabel.tabWithCount.text(L10n.text(tab), state.files.count)
        default: L10n.text(tab)
        }
    }
}

enum FileSizeText {
    static func text(bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}
