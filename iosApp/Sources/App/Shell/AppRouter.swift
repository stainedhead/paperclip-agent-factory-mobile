import Observation

/// The five tabs, in display order.
enum AppTab: String, CaseIterable, Identifiable {
    case inbox
    case factory
    case work
    case agents
    case factories

    var id: String { rawValue }

    /// Only the Inbox carries a badge: the server's count of what needs the person (`deskBadgeCount`).
    func badge(inboxBadgeCount: Int) -> Int? {
        self == .inbox && inboxBadgeCount > 0 ? inboxBadgeCount : nil
    }
}

/// Where a push goes. Cases carry ids only; the destination view looks up its own model. Screens arrive in later
/// milestones, until then every route shows a labelled placeholder.
enum AppRoute: Hashable {
    case decision(id: String)
    case approval(id: String)
    case issue(id: String)
    case agent(id: String)
    case run(id: String)
}

enum AppSheet: Hashable, Identifiable {
    case budgetIncident(id: String)
    case settings

    var id: Self { self }
}

/// Navigation state for the tab shell: the selected tab, one stack per tab, and the presented sheet.
@MainActor
@Observable
final class AppRouter {
    private(set) var selectedTab: AppTab = .inbox
    private(set) var sheet: AppSheet?
    private var paths: [AppTab: [AppRoute]] = [:]

    var activeRoutes: [AppRoute] {
        AppTab.allCases.flatMap { paths[$0] ?? [] }
    }

    func path(for tab: AppTab) -> [AppRoute] {
        paths[tab] ?? []
    }

    /// Selecting the tab that is already selected returns to its root, as the platform does.
    func select(_ tab: AppTab) {
        if tab == selectedTab { paths[tab] = [] }
        selectedTab = tab
    }

    func push(_ route: AppRoute, in tab: AppTab? = nil) {
        let target = tab ?? selectedTab
        selectedTab = target
        paths[target, default: []].append(route)
    }

    func setPath(_ path: [AppRoute], for tab: AppTab) {
        paths[tab] = path
    }

    func popToRoot(_ tab: AppTab) {
        paths[tab] = []
    }

    func present(_ sheet: AppSheet) {
        self.sheet = sheet
    }

    func dismissSheet() {
        sheet = nil
    }
}
