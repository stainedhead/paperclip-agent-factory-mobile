import Foundation
import PaperclipKit

/// What the Inbox card knew, turned into what the shared Decision screen accepts. The shared parser reads
/// `today`, `this_week`, `whenever` or an ISO date; this only writes the same words back.
enum DecisionHintMapping {
    static func hints(originAgentName: String?, decideBy: DecideByUi, dismissalKey: String?) -> DecisionHints {
        DecisionHints(originAgentName: originAgentName, decideBy: decideBy.hintText, dismissalKey: dismissalKey)
    }

    static func hints(_ item: InboxItemUi) -> DecisionHints {
        hints(originAgentName: item.originAgentName, decideBy: item.decideBy, dismissalKey: item.dismissalKey)
    }

}

/// The words of the Decision screen, composed from the shared UI model. Nothing here decides anything: which options
/// exist, what is enabled and what the result is come from the state; this only picks the sentences.
@MainActor
enum DecisionPresentation {
    /// "in_progress" reads "in progress": the server's words with the underscores taken out.
    static func prettify(_ raw: String) -> String {
        raw.replacingOccurrences(of: "_", with: " ")
    }

    /// Only a status is a server word ("in_progress"); a title, a comment excerpt or a name is shown as written, so the
    /// line says exactly what the option will do.
    static func effectLine(_ effect: EffectSummaryUi) -> String {
        let detail = effect.detail ?? ""
        return L10n.text(effect.kind, effect.kind == .updateIssueStatus ? prettify(detail) : detail)
    }

    /// "Expires Nov 3, 2026 at 5:00 PM": when the decision lapses by itself.
    static func expiryText(epochMillis: Int64, locale: Locale = .current, timeZone: TimeZone = .current) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000)
        let style = Date.FormatStyle(date: .abbreviated, time: .shortened, locale: locale, timeZone: timeZone)
        return DecisionLabel.expires.text(date.formatted(style))
    }

    /// "update_issue_status" reads "Update issue status".
    static func resultTitle(_ result: EffectResultUi) -> String {
        let words = prettify(result.effectType)
        return words.prefix(1).uppercased() + words.dropFirst()
    }

    static func reasonText(_ reason: String?) -> String? {
        guard let reason, !reason.isEmpty else { return nil }
        return reason == "target_changed" ? DecisionLabel.reasonTargetChanged.text : prettify(reason)
    }

    /// `nil` when there is no deadline, so the line is left out rather than saying "no deadline".
    static func decideByText(_ decideBy: DecideByUi) -> String? {
        switch decideBy.kind {
        case .none: nil
        case .onDate: decideBy.isoDate.map { L10n.text(DecideByKindUi.onDate, formattedDate($0)) }
        default: L10n.text(decideBy.kind)
        }
    }

    static func finalText(_ final: DecisionFinalUi) -> String {
        if final.kind == .decided {
            guard let chosen = final.chosenOptionLabel else { return DecisionLabel.finalDecidedNoOption.text }
            return L10n.text(DecisionFinalKind.decided, chosen)
        }
        return L10n.text(final.kind)
    }

    /// "3 / 40" under an input that has a limit; nothing for one that has none.
    static func counter(_ input: InputUi) -> String? {
        input.maxLength.map { DecisionLabel.counter.text("\(input.value.count)", "\($0)") }
    }

    /// One sentence per option for VoiceOver: the label, what asks Face ID, then each effect.
    static func optionLabel(_ option: OptionUi) -> String {
        var parts = [option.label]
        if option.isDestructive { parts.append(DecisionLabel.destructive.text) }
        if option.needsDeviceAuth { parts.append(DecisionLabel.asksFaceID.text) }
        parts += option.effects.map(effectLine)
        if option.effects.contains(where: \.isStrict) { parts.append(DecisionLabel.effectStrict.text) }
        return parts.joined(separator: ". ")
    }

    private static func formattedDate(_ iso: String) -> String {
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.timeZone = TimeZone(identifier: "UTC")
        guard let date = parser.date(from: iso) else { return iso }
        let style = Date.FormatStyle(
            date: .abbreviated, time: .omitted, timeZone: TimeZone(identifier: "UTC") ?? .current)
        return date.formatted(style)
    }
}
