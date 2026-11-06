import Foundation
import PaperclipKit

/// The words of the Approval screen, composed from the shared UI model. Nothing here decides anything.
@MainActor
enum ApprovalPresentation {
    private static let acronyms: [String: String] = [
        "qa": "QA", "cto": "CTO", "ceo": "CEO", "cfo": "CFO", "pm": "PM", "devops": "DevOps", "ux": "UX"
    ]

    /// "qa" reads "QA", "product_designer" reads "Product designer": a lookup for the common roles, otherwise the
    /// server's word with underscores taken out and the first letter raised.
    static func roleName(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        if let known = acronyms[raw.lowercased()] { return known }
        let words = raw.replacingOccurrences(of: "_", with: " ")
        return words.prefix(1).uppercased() + words.dropFirst()
    }

    /// The approval has no title of its own: the payload names what is being asked.
    static func title(payload: ApprovalPayloadUi?, type: ApprovalTypeUi?) -> String {
        let fallback = type.map { L10n.text($0) } ?? ApprovalLabel.navTitle.text
        switch payload {
        case let hire as ApprovalPayloadUiHireAgent:
            return (hire.name ?? hire.title).map { ApprovalLabel.hireTitle.text($0) } ?? fallback
        case let strategy as ApprovalPayloadUiCeoStrategy: return strategy.title ?? fallback
        case let budget as ApprovalPayloadUiBudgetOverride: return budget.scopeName ?? fallback
        case let board as ApprovalPayloadUiBoardRequest: return board.title ?? fallback
        default: return fallback
        }
    }

    static func tone(_ status: ApprovalStatusUi) -> StatusPill.Tone { StatusPill.Tone(status.tone) }

    /// What a resolved or waiting approval says about itself (`approvalDone.<status>`).
    static func doneText(_ status: ApprovalStatusUi) -> String {
        L10n.resolve("approvalDone.\(status)")
    }

    static func author(_ comment: CommentUi) -> String {
        comment.isFromAgent ? ApprovalLabel.commentAgent.text : ApprovalLabel.commentYou.text
    }
}
