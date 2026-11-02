import Foundation
import PaperclipKit

/// Where a card goes when opened. A lookup over the shared `OpenTargetUi`: the shared policy decided the kind; this
/// only maps it onto a route, a sheet or a web page.
enum InboxDestination: Equatable {
    case route(AppRoute)
    case sheet(AppSheet)
    case web(URL)
    case none

    init(target: OpenTargetUi, webUrl: String?) {
        switch target.kind {
        case .decision: self = .route(.decision(id: target.id))
        case .approval: self = .route(.approval(id: target.id))
        case .budgetIncident: self = .sheet(.budgetIncident(id: target.id))
        case .interaction, .issue: self = .route(.issue(id: target.issueId ?? target.id))
        case .run: self = .route(.run(id: target.id))
        case .agent: self = .route(.agent(id: target.id))
        case .webOnly: self = Self.web(webUrl)
        }
    }

    /// Only a web address is opened: a link the server sent is never handed to the system as anything else.
    private static func web(_ address: String?) -> InboxDestination {
        guard let address, let url = URL(string: address), ["http", "https"].contains(url.scheme?.lowercased()),
            url.host != nil
        else { return .none }
        return .web(url)
    }
}

/// What a card says, composed from the shared UI model. Nothing here decides anything: severity, kind, the actions
/// offered and the order come from the state; this only picks the words and joins them.
@MainActor
struct InboxCardPresentation: Equatable {
    let severity: SeverityUi
    let kindLine: String
    let title: String
    let context: String
    let age: String

    init(item: InboxItemUi, referenceDate: Date?) {
        self.init(
            kind: item.kind, rawKind: item.rawKind, title: item.title, identifier: item.identifier,
            severity: item.severity, whyNow: item.whyNow, origin: item.originAgentName,
            activityAtEpochMillis: item.activityAtEpochMillis, referenceDate: referenceDate)
    }

    init(
        kind: SourceKindUi, rawKind: String?, title: String?, identifier: String?, severity: SeverityUi,
        whyNow: String, origin: String?, activityAtEpochMillis: Int64, referenceDate: Date?
    ) {
        let kindName = kind == .unsupported ? (rawKind ?? kind.cardLabel) : kind.cardLabel
        self.severity = severity
        kindLine = [kindName, identifier].compactMap { $0 }.joined(separator: " \u{00B7} ")
        self.title = title ?? kindName
        context = Self.context(whyNow: whyNow, origin: origin)
        let seconds = (referenceDate ?? Date()).timeIntervalSince1970 - Double(activityAtEpochMillis) / 1000
        age = RelativeAge.text(seconds: seconds)
    }

    /// The reason the card is here, plus the agent it came from when the reason does not already name it.
    private static func context(whyNow: String, origin: String?) -> String {
        guard let origin, !origin.isEmpty, !whyNow.localizedCaseInsensitiveContains(origin) else { return whyNow }
        return "\(whyNow) \u{00B7} \(origin)"
    }

    /// One spoken sentence per card, so VoiceOver reads it as a unit and the buttons stay separate.
    var accessibilityLabel: String {
        let severityName = SeverityLabel.name(severity)
        return [severityName, kindLine, title, context, InboxLabel.ageAgo.text(age)].joined(separator: ". ")
    }
}

enum InboxHeader {
    /// "Home factory · Acme Labs": the factory and company whose Inbox this is.
    static func factoryLabel(factory: String?, company: String?) -> String {
        [factory, company].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " \u{00B7} ")
    }
}

/// Which buttons a card shows. The shared state decides which inline actions exist for this person (`inlineActions`);
/// this only picks the navigation button that goes with the kind: "Review options" for a decision, "Details" for an
/// approval, "Open in the web app" for a kind this app cannot show, a chevron for the rest.
struct InboxCardActions: Equatable {
    enum Link: Equatable {
        case reviewOptions
        case details
        case openInWeb
        case chevron
    }

    let inline: [InlineActionUi]
    let link: Link

    init(kind: SourceKindUi, target: OpenTargetUi, inline: [InlineActionUi]) {
        self.inline = inline
        switch (kind, target.kind) {
        case (.decision, _): link = .reviewOptions
        case (.approval, _): link = .details
        case (_, .webOnly): link = .openInWeb
        default: link = .chevron
        }
    }

    init(item: InboxItemUi) {
        self.init(kind: item.kind, target: item.open, inline: item.inlineActions)
    }

    func offers(_ action: InlineActionUi) -> Bool { inline.contains(action) }
}
