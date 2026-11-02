import Testing

@testable import iosApp

/// UI-003: five tabs, one navigation stack each, and sheets.
@MainActor
struct AppRouterTests {
    @Test func theTabsAreInTheDocumentedOrder() {
        #expect(AppTab.allCases.map(\.rawValue) == ["inbox", "factory", "work", "agents", "factories"])
        #expect(AppTab.allCases.count == 5)
    }

    @Test func theInboxBadgeIsTheSharedBadgeCountAndOnlyOnTheInboxTab() {
        #expect(AppTab.inbox.badge(inboxBadgeCount: 3) == 3)
        #expect(AppTab.inbox.badge(inboxBadgeCount: 0) == nil)
        #expect(AppTab.factory.badge(inboxBadgeCount: 3) == nil)
    }

    @Test func everyTabHasItsOwnStackAndKeepsItWhenAnotherTabIsSelected() {
        let router = AppRouter()
        router.push(.issue(id: "pap-1204"))
        router.select(.agents)
        router.push(.agent(id: "agent-eng-1"))

        #expect(router.path(for: .inbox) == [.issue(id: "pap-1204")])
        #expect(router.path(for: .agents) == [.agent(id: "agent-eng-1")])
        router.select(.inbox)
        #expect(router.selectedTab == .inbox)
        #expect(router.path(for: .inbox) == [.issue(id: "pap-1204")])
    }

    @Test func aPushCanTargetATabAndSelectsIt() {
        let router = AppRouter()
        router.push(.run(id: "run-1204-live"), in: .agents)
        #expect(router.selectedTab == .agents)
        #expect(router.path(for: .agents) == [.run(id: "run-1204-live")])
    }

    @Test func popToRootClearsOnlyThatTab() {
        let router = AppRouter()
        router.push(.decision(id: "dec-1182"))
        router.push(.approval(id: "ap-hire-qa"), in: .work)
        router.popToRoot(.work)
        #expect(router.path(for: .work).isEmpty)
        #expect(router.path(for: .inbox) == [.decision(id: "dec-1182")])
    }

    @Test func reselectingTheSelectedTabReturnsToItsRoot() {
        let router = AppRouter()
        router.push(.decision(id: "dec-1182"))
        router.select(.inbox)
        #expect(router.path(for: .inbox).isEmpty)
    }

    @Test func theBindingWritesBackSystemPops() {
        let router = AppRouter()
        router.push(.decision(id: "a"))
        router.push(.approval(id: "b"))
        router.setPath([.decision(id: "a")], for: .inbox)
        #expect(router.path(for: .inbox) == [.decision(id: "a")])
    }

    @Test func sheetsPresentAndDismiss() {
        let router = AppRouter()
        router.present(.budgetIncident(id: "inc-eng-2"))
        #expect(router.sheet == .budgetIncident(id: "inc-eng-2"))
        router.dismissSheet()
        #expect(router.sheet == nil)
    }

    @Test func everyRouteInUseIsListedSoOwnersKnowWhichModelsToKeep() {
        let router = AppRouter()
        router.push(.decision(id: "a"))
        router.push(.agent(id: "b"), in: .agents)
        #expect(router.activeRoutes == [.decision(id: "a"), .agent(id: "b")])
    }
}
