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
        // On iOS 26.5 the tab bar does not expose the badge through the button's label or value (the failure
        // hierarchies show label "Inbox" only, with the diagnostics trace at badge=3), so that line is the check;
        // when the tab does expose its badge it must agree.
        let counts = app.element("inbox-due-count")
        XCTAssertTrue(counts.waitForExistence(timeout: 30), "the Inbox count line never appeared")
        XCTAssertTrue(counts.label.hasPrefix("3 "), "the Inbox badge count is not 3: \(counts.label)")
        let inbox = tabs["Inbox"]
        let exposed = ((inbox.value as? String) ?? "") + inbox.label
        if exposed.contains(where: \.isNumber) {
            XCTAssertTrue(exposed.contains("3"), "the tab badge disagrees with the state: \(exposed)")
        }
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
        let app = XCUIApplication()
        app.launch()
        let demo = app.buttons["try-demo-button"]
        XCTAssertTrue(demo.waitForExistence(timeout: 15))
        demo.tap()
        waitForShell(app)
    }

    /// Baseline for the shell (acceptance criterion 5). Findings are logged and attached, not failed on: the list in
    /// implementation-notes.md records what remains and why.
    @MainActor
    func testAccessibilityAuditBaselineOfTheShell() throws {
        let app = launch()
        waitForShell(app)
        var findings: [String] = []
        try app.performAccessibilityAudit { issue in
            findings.append("\(issue.auditType) | \(issue.compactDescription) | \(issue.element?.label ?? "-")")
            return true
        }
        let attachment = XCTAttachment(string: findings.joined(separator: "\n"))
        attachment.name = "accessibility-audit-baseline"
        attachment.lifetime = .keepAlways
        add(attachment)
        print("ACCESSIBILITY-AUDIT-BASELINE count=\(findings.count)")
        for finding in findings { print("ACCESSIBILITY-AUDIT-FINDING \(finding)") }
    }
}
