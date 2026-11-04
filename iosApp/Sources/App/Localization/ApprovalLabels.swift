import Foundation
import PaperclipKit

extension ApprovalTypeUi: CatalogKey { static var catalogNamespace: String { "approvalType" } }
extension ApprovalStatusUi: CatalogKey { static var catalogNamespace: String { "approvalStatus" } }
extension ApprovalActionUi: CatalogKey { static var catalogNamespace: String { "approvalAction" } }
extension NotePromptKind: CatalogKey { static var catalogNamespace: String { "notePrompt" } }

/// Labels of the Approval screen. Raw values are catalog keys (`approval.<case>`).
enum ApprovalLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "approval" }

    case navTitle
    case hireTitle
    case kindLine
    case role
    case reportsTo
    case adapter
    case monthlyBudget
    case name
    case jobTitle
    case skills
    case capabilities
    case plan
    case scope
    case window
    case limit
    case observed
    case guidance
    case summary
    case recommended
    case nextOnApproval
    case risks
    case proposedComment
    case unsupported
    case linkedWork
    case openIssueHint
    case discussion
    case noDiscussion
    case commentLabel
    case commentPlaceholder
    case commentPost
    case commentAgent
    case commentYou
    case decisionNote
    case rejectTitle
    case rejectMessage
    case rejectConfirm
    case reviseTitle
    case reviseMessage
    case reviseConfirm
    case noteLabel
    case rejectPlaceholder
    case revisePlaceholder
    case working

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
