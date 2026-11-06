import XCTest

/// Launch and read helpers for the Factory home, Agents, Agent detail and Run viewer UI tests. They only drive and
/// read the screens: every assertion is against an accessibility identifier, never a position.
enum OpsIds {
    static let liveRun = "run-1204-live"
    static let doneRun = "run-1204-done"
    static let engineer1 = "agent-eng-1"
    static let engineer2 = "agent-eng-2"
    static let cto = "agent-cto"
    static let qaAgent = "agent-qa"
    static let designer = "agent-design"
    static let ceo = "agent-ceo"
}

extension XCTestCase {
    /// Launches the demo on `tab` (the unlock takes the first scripted Face ID result) and waits for `root` to show.
    @MainActor
    func launchOperations(
        tab: String, root: String, biometrics: String = "approved", extra: [String] = []
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments =
            ["-demo", "-demo-tab", tab, "-demo-biometrics", biometrics, "-demo-diagnostics"] + extra
        app.launch()
        // A screen opened at launch (`-demo-open`) hides the tab bar, so the tab bar is only awaited for a tab root.
        let reachesTabBar = !extra.contains("-demo-open")
        if reachesTabBar {
            XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab bar never appeared")
        }
        app.waitUntilUnlocked()
        XCTAssertTrue(app.element(root).waitForExistence(timeout: 30), "\(root) never appeared")
        return app
    }

    @MainActor
    func launchFactoryHome(biometrics: String = "approved", extra: [String] = []) -> XCUIApplication {
        let app = launchOperations(tab: "factory", root: "factory-root", biometrics: biometrics, extra: extra)
        XCTAssertTrue(app.element("factory-spend").waitForExistence(timeout: 30), "Factory home never loaded")
        return app
    }

    @MainActor
    func launchAgents(biometrics: String = "approved", extra: [String] = []) -> XCUIApplication {
        let app = launchOperations(tab: "agents", root: "agents-root", biometrics: biometrics, extra: extra)
        XCTAssertTrue(app.element("agent-row-\(OpsIds.cto)").waitForExistence(timeout: 30), "the agents never loaded")
        return app
    }
}

extension XCUIApplication {
    /// Scrolls `container` up until `identifier` exists and is on screen (lazy rows are not built until near).
    @MainActor
    func scroll(to identifier: String, in container: String) {
        var swipes = 0
        while !(element(identifier).exists && element(identifier).isHittable) && swipes < 8 {
            element(container).swipeUp()
            swipes += 1
        }
        settle(element(identifier))
    }

    /// Waits until the element stops moving (a swipe's momentum scroll is still running when the swipe returns, and a
    /// tap on a moving row is lost). Bounded at three seconds; the caller's next step still fails if it never rests.
    @MainActor
    func settle(_ target: XCUIElement) {
        let deadline = Date().addingTimeInterval(3)
        var last = target.frame
        while Date() < deadline {
            Thread.sleep(forTimeInterval: 0.3)
            let now = target.frame
            if now == last { return }
            last = now
        }
    }

    /// Taps a row that opens a screen and waits for that screen. A tap that lands while the list is still moving or
    /// the screen is still transitioning is lost, so when the screen has not appeared after `patience` seconds and the
    /// row is still on screen (nothing opened), it is tapped again. A row that has gone (the screen opened late) is
    /// never tapped twice, and a screen that never opens still fails after the last attempt.
    @MainActor
    func tapOpening(
        _ row: XCUIElement, screen: XCUIElement, attempts: Int = 3, patience: TimeInterval = 10
    ) -> Bool {
        for attempt in 1...attempts {
            if row.exists && row.isHittable { row.tap() }
            if screen.waitForExistence(timeout: patience) { return true }
            if attempt == attempts { break }
        }
        return screen.exists
    }

    @MainActor
    func scrollToTop(of container: String) {
        for _ in 0..<4 { element(container).swipeDown() }
    }

    func selectTab(_ title: String) { tabBars.firstMatch.buttons[title].tap() }
}

extension XCTestCase {
    /// The system audit of a tab root, with findings under the floating tab bar logged and not returned.
    @MainActor
    func auditTabRoot(_ app: XCUIApplication, screen: String) throws -> [String] {
        try auditInboxScreen(app, screen: screen)
    }
}
