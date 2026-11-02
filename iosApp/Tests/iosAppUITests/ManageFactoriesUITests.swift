import XCTest

/// Screen 16 on the demo graph: reorder, and remove with the device owner's approval.
final class ManageFactoriesUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// The launch unlock takes the first scripted result, so the removals see `cancelled` then `approved`.
    @MainActor
    private func openManage() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-demo", "-demo-biometrics", "approved,cancelled,approved", "-demo-diagnostics",
            "-demo-tab", "factories", "-demo-open", "manage"
        ]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab bar never appeared")
        XCTAssertTrue(app.element("manage-row-demo-home").waitForExistence(timeout: 15))
        return app
    }

    @MainActor
    private func promptCount(_ app: XCUIApplication) -> String? {
        let element = app.staticTexts["diagnostics-biometric-prompts"]
        return element.waitForExistence(timeout: 5) ? element.value as? String : nil
    }

    @MainActor
    func testTheRowsShowNameAddressAndStatusAsInTheMock() {
        let app = openManage()
        XCTAssertEqual(app.staticTexts["manage-name-demo-home"].label, "Home factory")
        XCTAssertEqual(app.staticTexts["manage-address-demo-test"].label, "http://localhost:3100")
        XCTAssertEqual(app.staticTexts["manage-status-demo-cloud"].label, "Signed out")
        XCTAssertTrue(app.staticTexts["manage-count"].label.contains("3"))
        XCTAssertTrue(app.buttons["add-factory-button"].exists)
    }

    @MainActor
    func testRemovingNeedsFaceIDCancelledKeepsTheFactoryApprovedRemovesIt() {
        let app = openManage()
        XCTAssertEqual(promptCount(app), "1", "the launch unlock is the first prompt")

        app.buttons["manage-remove-demo-cloud"].tap()
        XCTAssertTrue(app.element("confirm-destructive-sheet").waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Remove \u{201C}Cloud factory\u{201D}?"].exists)
        app.buttons["confirm-destructive-confirm"].tap()

        XCTAssertTrue(app.staticTexts["confirm-destructive-issue"].waitForExistence(timeout: 10))
        XCTAssertEqual(promptCount(app), "2")
        XCTAssertTrue(app.element("confirm-destructive-sheet").exists, "the sheet stays after a cancelled prompt")

        app.buttons["confirm-destructive-confirm"].tap()
        let gone = NSPredicate(format: "exists == false")
        expectation(for: gone, evaluatedWith: app.element("manage-row-demo-cloud"))
        waitForExpectations(timeout: 15)
        XCTAssertEqual(promptCount(app), "3")
        XCTAssertTrue(app.element("manage-row-demo-home").exists)
        XCTAssertTrue(app.element("manage-row-demo-test").exists)
    }

    @MainActor
    func testKeepClosesTheSheetWithoutAskingForFaceID() {
        let app = openManage()
        app.buttons["manage-remove-demo-test"].tap()
        XCTAssertTrue(app.buttons["confirm-destructive-cancel"].waitForExistence(timeout: 10))
        app.buttons["confirm-destructive-cancel"].tap()
        XCTAssertFalse(app.element("confirm-destructive-sheet").waitForExistence(timeout: 2))
        XCTAssertEqual(promptCount(app), "1")
        XCTAssertTrue(app.element("manage-row-demo-test").exists)
    }

    @MainActor
    func testDraggingTheHandleReordersTheFactories() {
        let app = openManage()
        let home = app.element("manage-row-demo-home")
        let cloud = app.element("manage-row-demo-cloud")
        XCTAssertLessThan(home.frame.minY, cloud.frame.minY)

        let handle = app.buttons["Reorder Remove Cloud factory"]
        XCTAssertTrue(handle.waitForExistence(timeout: 5))
        let start = handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let target = home.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1))
        start.press(forDuration: 0.6, thenDragTo: target)

        let moved = NSPredicate { _, _ in
            app.element("manage-row-demo-cloud").frame.minY < app.element("manage-row-demo-home").frame.minY
        }
        expectation(for: moved, evaluatedWith: nil)
        waitForExpectations(timeout: 15)
    }

    @MainActor
    func testAccessibilityAuditOfManageFactories() throws {
        let app = openManage()
        let findings = try auditAccessibility(app, screen: "manage-factories")
        // Listed in implementation-notes.md: system glass "Done", list header/footer contrast and Dynamic Type notes,
        // and "Text clipped" on rows. Anything else fails.
        let known = ["Text clipped", "| Done", "Contrast nearly passed", "Dynamic Type font sizes"]
        let others = findings.filter { finding in !known.contains { finding.contains($0) } }
        XCTAssertTrue(others.isEmpty, others.joined(separator: "\n"))
    }
}
