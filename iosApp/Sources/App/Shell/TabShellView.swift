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
                    root(for: tab)
                        .navigationDestination(for: AppRoute.self) {
                            RouteDestination(route: $0, environment: environment)
                        }
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.symbol)
                        .accessibilityIdentifier("tab-\(tab.rawValue)")
                }
                .badge(tab.badge(inboxBadgeCount: Int(environment.inbox?.state.badgeCount ?? 0)) ?? 0)
                .tag(tab)
            }
        }
        .sheet(item: sheet) { SheetDestination(sheet: $0) }
    }

    private var sheet: Binding<AppSheet?> {
        Binding(
            get: { environment.router.sheet },
            set: { if $0 == nil { environment.router.dismissSheet() } })
    }

    @ViewBuilder private func root(for tab: AppTab) -> some View {
        switch tab {
        case .inbox: InboxView(environment: environment)
        case .factories: FactoriesView(environment: environment)
        default: PlaceholderRootView(tab: tab, environment: environment)
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
