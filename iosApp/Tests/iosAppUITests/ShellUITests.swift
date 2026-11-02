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
        let app = launch()
        waitForShell(app)

        let tabs = app.tabBars.firstMatch.buttons
        XCTAssertEqual(tabs.count, 5)
        for title in ["Inbox", "Factory", "Work", "Agents", "Factories"] {
            XCTAssertTrue(tabs[title].exists, "missing tab \(title)")
        }
        let inbox = tabs["Inbox"]
        let badge = NSPredicate { element, _ in
            // iOS 26.5 may expose the badge in the label instead of the value.
            guard let button = element as? XCUIElement else { return false }
            return ((button.value as? String) ?? "").contains("3") || button.label.contains("3")
        }
        expectation(for: badge, evaluatedWith: inbox)
        waitForExpectations(timeout: 30)
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
