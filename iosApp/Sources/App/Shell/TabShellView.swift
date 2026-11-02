import PaperclipKit
import SwiftUI

/// Five tabs, one navigation stack each. The Inbox badge is the shared Inbox state's `badgeCount` (the server's
/// `deskBadgeCount`); the shell never computes it.
struct TabShellView: View {
    let environment: AppEnvironment

    var body: some View {
        TabView(selection: selection) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack(path: path(for: tab)) {
                    PlaceholderRootView(tab: tab, environment: environment)
                        .navigationDestination(for: AppRoute.self) { RoutePlaceholderView(route: $0) }
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.symbol)
                        .accessibilityIdentifier("tab-\(tab.rawValue)")
                }
                .badge(tab.badge(inboxBadgeCount: Int(environment.inbox?.state.badgeCount ?? 0)) ?? 0)
                .tag(tab)
            }
        }
    }

    private var selection: Binding<AppTab> {
        Binding(
            get: { environment.router.selectedTab },
            set: { environment.router.select($0) })
    }

    private func path(for tab: AppTab) -> Binding<[AppRoute]> {
        Binding(
            get: { environment.router.path(for: tab) },
            set: { environment.router.setPath($0, for: tab) })
    }
}

extension AppTab {
    var title: String {
        switch self {
        case .inbox: ShellLabel.tabInbox.text
        case .factory: ShellLabel.tabFactory.text
        case .work: ShellLabel.tabWork.text
        case .agents: ShellLabel.tabAgents.text
        case .factories: ShellLabel.tabFactories.text
        }
    }

    var symbol: String {
        switch self {
        case .inbox: "tray"
        case .factory: "house"
        case .work: "checklist"
        case .agents: "person.2"
        case .factories: "rectangle.stack"
        }
    }
}
