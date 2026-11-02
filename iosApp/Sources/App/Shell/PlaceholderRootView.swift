import PaperclipKit
import SwiftUI

/// The root of a tab whose real screen arrives in a later milestone. Clearly a placeholder: it says so on screen.
/// It shows one real fact from the shared state so the wiring is visible: the Inbox count and the factory name.
struct PlaceholderRootView: View {
    let tab: AppTab
    let environment: AppEnvironment

    var body: some View {
        VStack(spacing: Spacing.extraLarge) {
            EmptyState(
                symbol: tab.symbol,
                title: ShellLabel.placeholderTitle.text,
                message: ShellLabel.placeholderMessage.text)
            if tab == .inbox, let inbox = environment.inbox {
                Text("\(inbox.state.badgeCount) / \(inbox.state.totalCount)")
                    .font(Typography.secondary)
                    .accessibilityIdentifier("placeholder-inbox-counts")
            }
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

/// Destination for every route until its screen is built.
struct RoutePlaceholderView: View {
    let route: AppRoute

    var body: some View {
        EmptyState(
            symbol: "hammer",
            title: ShellLabel.placeholderTitle.text,
            message: ShellLabel.placeholderMessage.text
        )
        .accessibilityIdentifier("route-placeholder")
    }
}
