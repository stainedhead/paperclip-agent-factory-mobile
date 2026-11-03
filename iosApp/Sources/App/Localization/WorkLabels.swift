import Foundation
import PaperclipKit

extension IssueStatusUi: CatalogKey { static var catalogNamespace: String { "issueStatus" } }
extension IssuePriorityUi: CatalogKey { static var catalogNamespace: String { "issuePriority" } }
extension IssueFilterUi: CatalogKey { static var catalogNamespace: String { "issueFilter" } }
extension IssueTab: CatalogKey { static var catalogNamespace: String { "issueTab" } }
extension CommentAuthorUi: CatalogKey { static var catalogNamespace: String { "commentAuthor" } }
extension InteractionKindUi: CatalogKey { static var catalogNamespace: String { "interactionKind" } }
extension InteractionStatusUi: CatalogKey { static var catalogNamespace: String { "interactionStatus" } }
extension InteractionReadOnlyUi: CatalogKey { static var catalogNamespace: String { "interactionReadOnly" } }
extension InteractionPrimaryUi: CatalogKey { static var catalogNamespace: String { "interactionPrimary" } }
extension VerdictUi: CatalogKey { static var catalogNamespace: String { "verdict" } }

/// Labels of the Work list. Raw values are catalog keys (`work.<case>`).
enum WorkLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "work" }

    case filtersLabel
    case searchLabel
    case searchPlaceholder
    case countLine
    case countOne
    case emptyTitle
    case emptyMessage
    case emptySearchMessage
    case loadingMore
    case create
    case createTitle
    case createField
    case createPlaceholder
    case createPriority
    case createSave
    case createCancel
    case unassigned
    case assignedToYou
    case openHint

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}

/// Labels of the Issue screen. Raw values are catalog keys (`issue.<case>`).
enum IssueLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "issue" }

    case tabsLabel
    case tabWithCount
    case partOf
    case blockedBy
    case subtasks
    case addSubtask
    case subtaskTitle
    case subtaskField
    case subtaskPlaceholder
    case subtaskSave
    case subtaskCancel
    case noThread
    case planEmpty
    case runsEmpty
    case filesEmpty
    case filesNote
    case loadingTab
    case commentField
    case commentPlaceholder
    case commentSend
    case reopenToggle
    case interruptToggle
    case statusMenu
    case statusMenuTitle
    case assigneeMenu
    case assigneeUnassigned
    case assigneeMe
    case assigneeChange
    case interactionsUnavailable
    case authorYou
    case authorAgent
    case authorSystem
    case authorLine
    case questionsMore
    case optionalQuestion
    case otherField
    case answerPlaceholder
    case rejectReason
    case rejectReasonRequired
    case itemReason
    case openInWeb
    case working
    case updated
    case fileLine
    case commentsLabel
    case selected

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
