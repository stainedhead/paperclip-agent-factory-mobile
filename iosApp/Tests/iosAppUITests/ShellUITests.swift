import XCTest

/// The tab shell, the app lock and the first-run hand-off, driven on the demo graph (UI-001 to UI-004).
final class ShellUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func launch(_ extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demo"] + extra
        app.launch()
        return app
    }

    private func waitForShell(_ app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(
            app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab bar never appeared", file: file, line: line)
    }

    private func promptCount(_ app: XCUIApplication) -> String? {
        let element = app.staticTexts["diagnostics-biometric-prompts"]
        return element.waitForExistence(timeout: 5) ? element.value as? String : nil
    }

    @MainActor
    func testALaunchWithoutDemoSaysLiveFactoriesAreNotInThisBuildAndOffersNothingToApprove() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.element("startup-notice").waitForExistence(timeout: 30), "the notice never appeared")
        XCTAssertTrue(app.staticTexts["Live factories are not available yet"].exists)
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        XCTAssertEqual(app.buttons.count, 0, "nothing to tap: no pairing, no demo, no approvals")
    }

    @MainActor
    func testDemoLaunchShowsFiveTabsAndTheInboxBadgeFromTheDemoData() {
        // The diagnostics trace (lock, load, Inbox phase and badge) lands in the failure hierarchy of a slow start.
        let app = launch(["-demo-diagnostics"])
        waitForShell(app)
        app.waitUntilUnlocked()

        let tabs = app.tabBars.firstMatch.buttons
        XCTAssertEqual(tabs.count, 5)
        for title in ["Inbox", "Factory", "Work", "Agents", "Factories"] {
            XCTAssertTrue(tabs[title].exists, "missing tab \(title)")
        }
        // The shared Inbox state's badge count reaches the screen as the "N new or due today" line of the Inbox.
        let counts = app.element("inbox-due-count")
        XCTAssertTrue(counts.waitForExistence(timeout: 30), "the Inbox count line never appeared")
        XCTAssertTrue(counts.label.hasPrefix("3 "), "the Inbox badge count is not 3: \(counts.label)")
        // The tab button exposes its badge as its accessibility value (ADR-107), so the tab agrees with the state.
        waitForInboxBadge(app, 3)
        XCTAssertTrue(app.navigationBars["Inbox"].exists)
    }

    @MainActor
    func testSwitchingTabsShowsEachTabsOwnRoot() {
        let app = launch()
        waitForShell(app)

        for title in ["Factory", "Work", "Agents", "Factories", "Inbox"] {
            app.tabBars.firstMatch.buttons[title].tap()
            XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 5), "no \(title) root")
        }
        app.tabBars.firstMatch.buttons["Factories"].tap()
        XCTAssertEqual(app.staticTexts["factory-name-demo-home"].label, "Home factory")
    }

    @MainActor
    func testACancelledFaceIDKeepsTheLockAndTheNextTryUnlocks() {
        let app = launch(["-demo-biometrics", "cancelled,approved", "-demo-diagnostics"])

        XCTAssertTrue(app.buttons["unlock-button"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["unlock-issue"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.tabBars.firstMatch.exists, "the shell must stay hidden while locked")
        XCTAssertEqual(promptCount(app), "1")

        app.buttons["unlock-button"].tap()
        waitForShell(app)
        XCTAssertEqual(promptCount(app), "2")
    }

    @MainActor
    func testGoingToTheBackgroundLocksAgainAndFaceIDOpensIt() {
        let app = launch(["-demo-idle", "immediately", "-demo-biometrics", "approved,cancelled", "-demo-diagnostics"])
        waitForShell(app)

        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10))
        app.activate()

        XCTAssertTrue(app.buttons["unlock-button"].waitForExistence(timeout: 15), "the app did not lock again")
        XCTAssertTrue(app.staticTexts["unlock-issue"].waitForExistence(timeout: 5))
        app.buttons["unlock-button"].tap()
        waitForShell(app)
    }

    @MainActor
    func testTryTheDemoFactoryFromFirstRunOpensTheShell() {
        // A plain launch now shows the "not available in this build" notice; the first-run demo graph shows the button.
        let app = XCUIApplication()
        app.launchArguments = ["-demo-first-run"]
        app.launch()
        let demo = app.buttons["try-demo-button"]
        XCTAssertTrue(demo.waitForExistence(timeout: 15))
        demo.tap()
        waitForShell(app)
    }

    /// The system audit of the tab shell (acceptance criterion 5). Every finding fails the test; the audit set does not
    /// block CI (ADR-094), so what it finds is reported there and listed in implementation-notes.md.
    @MainActor
    func testAccessibilityAuditBaselineOfTheShell() throws {
        let app = launch()
        waitForShell(app)
        let findings = try auditScreen(app, screen: "shell")
        XCTAssertTrue(findings.isEmpty, findings.joined(separator: "\n"))
    }
}
