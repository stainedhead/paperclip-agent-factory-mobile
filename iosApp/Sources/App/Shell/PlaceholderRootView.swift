import PaperclipKit
import SwiftUI

/// The root of a tab whose real screen arrives in a later milestone. Clearly a placeholder: it says so on screen.
/// The Factories tab shows one real fact from the shared state so the wiring is visible: the factory name.
struct PlaceholderRootView: View {
    let tab: AppTab
    let environment: AppEnvironment

    var body: some View {
        VStack(spacing: Spacing.extraLarge) {
            EmptyState(
                symbol: tab.symbol,
                title: ShellLabel.placeholderTitle.text,
                message: ShellLabel.placeholderMessage.text)
            if tab == .factories, let name = environment.app.state.selectedInstanceName {
                Text(name)
                    .font(Typography.cardTitle)
                    .accessibilityIdentifier("placeholder-factory-name")
            }
        }
        .navigationTitle(tab.title)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("placeholder-\(tab.rawValue)")
    }
}

/// Destination for every route until its screen is built. It names the screen and the id it was opened for, so a
/// test (and a person) can tell where a card led.
struct RoutePlaceholderView: View {
    let route: AppRoute

    var body: some View {
        VStack(spacing: Spacing.extraLarge) {
            EmptyState(symbol: "hammer", title: ShellLabel.placeholderTitle.text, message: message)
            Text(identifier)
                .font(Typography.micro)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityIdentifier("route-placeholder-id")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("route-placeholder")
    }

    private var message: String {
        switch route {
        case .decision: InboxLabel.placeholderDecision.text
        case .approval: InboxLabel.placeholderApproval.text
        case .issue: InboxLabel.placeholderIssue.text
        case .run: InboxLabel.placeholderRun.text
        case .agent: InboxLabel.placeholderAgent.text
        default: InboxLabel.placeholderGeneric.text
        }
    }

    /// `decision:dec-1182`: the kind of screen and the id it was opened for.
    private var identifier: String {
        switch route {
        case .decision(let id): "decision:\(id)"
        case .approval(let id): "approval:\(id)"
        case .issue(let id): "issue:\(id)"
        case .agent(let id): "agent:\(id)"
        case .run(let id): "run:\(id)"
        default: "\(route)"
        }
    }
}
