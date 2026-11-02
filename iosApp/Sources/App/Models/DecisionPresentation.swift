import PaperclipKit

/// What the Inbox card knew, turned into what the shared Decision screen accepts. The shared parser reads
/// `today`, `this_week`, `whenever` or an ISO date; this only writes the same words back.
enum DecisionHintMapping {
    static func hints(originAgentName: String?, decideBy: DecideByUi, dismissalKey: String?) -> DecisionHints {
        DecisionHints(originAgentName: originAgentName, decideBy: decideByText(decideBy), dismissalKey: dismissalKey)
    }

    static func hints(_ item: InboxItemUi) -> DecisionHints {
        hints(originAgentName: item.originAgentName, decideBy: item.decideBy, dismissalKey: item.dismissalKey)
    }

    private static func decideByText(_ decideBy: DecideByUi) -> String? {
        switch decideBy.kind {
        case .today: "today"
        case .thisWeek: "this_week"
        case .whenever: "whenever"
        case .onDate: decideBy.isoDate
        case .none: nil
        }
    }
}
