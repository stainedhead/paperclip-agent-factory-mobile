import Foundation
import PaperclipKit

/// The words and figures of the Budget incident sheet, composed from the shared UI model.
@MainActor
enum BudgetPresentation {
    /// "$200 of $200 monthly budget · 100% used"
    static func usage(observed: MoneyUi, limit: MoneyUi, percent: Int, locale: Locale = .current) -> String {
        BudgetLabel.usage.text(
            MoneyFormat.text(observed, locale: locale), MoneyFormat.text(limit, locale: locale), "\(percent)%")
    }

    static func amountHelp(observed: MoneyUi, locale: Locale = .current) -> String {
        BudgetLabel.amountHelp.text(MoneyFormat.text(observed, locale: locale))
    }

    /// The share of the bar to fill: the state's whole percent, held between empty and full.
    static func gauge(percent: Int) -> Double {
        min(max(Double(percent) / 100, 0), 1)
    }
}

/// What the last action said, in words. The server's own reason for a refusal follows the message, so a person sees
/// why (for example "New budget must exceed current observed spend") and not only that it failed.
@MainActor
enum FeedbackPresentation {
    static func text(_ feedback: FeedbackUi) -> String {
        if let issue = feedback.deviceAuth { return L10n.text(issue) }
        if let invalid = feedback.invalid { return L10n.text(invalid) }
        if let error = feedback.error {
            let base = "\(L10n.text(feedback.kind)) \(L10n.text(error.key))"
            guard error.key == .rejected, let detail = error.detail, !detail.isEmpty else { return base }
            return "\(base) \(detail)"
        }
        return L10n.text(feedback.kind)
    }
}
