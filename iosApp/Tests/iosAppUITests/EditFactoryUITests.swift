import XCTest

/// Screen 9 on the demo graph: the Edit factory mock, rename, address check, pin, pair again, sign out, add.
final class EditFactoryUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func open(_ route: String, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments =
            ["-demo", "-demo-tab", "factories", "-demo-open", route, "-demo-diagnostics"] + extra
        app.launch()
        XCTAssertTrue(app.element("edit-factory").waitForExistence(timeout: 30), "the edit form never appeared")
        return app
    }

    @MainActor
    func testTheHomeFactoryShowsTheMockValues() {
        let app = open("edit:demo-home")
        XCTAssertEqual(app.textFields["edit-name"].value as? String, "Home factory")
        XCTAssertEqual(app.textFields["edit-address"].value as? String, "https://paperclip.tail1234.ts.net")
        XCTAssertTrue(app.element("edit-access-company-company-acme").waitForExistence(timeout: 10))
        XCTAssertTrue(app.element("edit-access-company-company-acme").label.contains("Owner"))
        XCTAssertTrue(app.element("edit-access-company-company-side").label.contains("Operator"))
        XCTAssertTrue(app.element("edit-board-key").label.contains("23 days"))
        XCTAssertTrue(app.element("edit-default-company").label.contains("Acme Labs"))
        XCTAssertTrue(app.buttons["edit-pair-again"].exists)
        XCTAssertTrue(app.buttons["edit-sign-out"].exists)
        XCTAssertTrue(app.buttons["edit-remove"].exists)
        XCTAssertTrue(app.buttons["edit-save-button"].isEnabled, "an unchanged edit can be saved")
    }

    @MainActor
    func testRenamingSavesAndTheNewNameShowsOnTheFactoriesList() {
        let app = open("edit:demo-home")
        let field = app.textFields["edit-name"]
        field.clearAndTypeText("Studio factory")
        app.buttons["edit-save-button"].tap()

        XCTAssertTrue(app.element("factories-root").waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["factory-name-demo-home"].label, "Studio factory")
    }

    @MainActor
    func testChangingTheAddressDisablesSaveUntilTheNewAddressIsChecked() {
        let app = open("edit:demo-home")
        let save = app.buttons["edit-save-button"]
        app.textFields["edit-address"].clearAndTypeText("https://other.example.com")

        XCTAssertFalse(save.isEnabled, "Save waits for a fresh check")
        XCTAssertTrue(app.element("edit-address-warning").exists)
        app.buttons["edit-check-address"].tap()
        XCTAssertTrue(app.element("edit-probe-result").waitForExistence(timeout: 10))
        let enabled = NSPredicate(format: "isEnabled == true")
        expectation(for: enabled, evaluatedWith: save)
        waitForExpectations(timeout: 10)
    }

    @MainActor
    func testThePinToggleOpensThePinEntry() {
        let app = open("edit:demo-home")
        let toggle = app.switches["edit-pin-toggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        XCTAssertEqual(toggle.value as? String, "0")
        XCTAssertFalse(app.textFields["edit-pin-field"].exists)
        // On a 874 pt tall screen (the CI simulator) the row starts below the fold: scroll it into view first.
        var swipes = 0
        while !toggle.isHittable && swipes < 5 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(toggle.isHittable, "The pin toggle never scrolled into view")
        toggle.tap()
        XCTAssertTrue(app.textFields["edit-pin-field"].waitForExistence(timeout: 10))
        XCTAssertEqual(toggle.value as? String, "1")
    }

    @MainActor
    func testPairingTheSignedOutCloudFactoryAgainSignsItIn() {
        let app = open("edit:demo-cloud")
        XCTAssertTrue(app.element("edit-status").label.contains("Key expired"))
        app.buttons["edit-pair-again"].tap()
        let connected = NSPredicate { _, _ in app.element("edit-status").label.contains("Connected") }
        expectation(for: connected, evaluatedWith: nil)
        waitForExpectations(timeout: 20)
    }

    @MainActor
    func testSignOutAsksFirstAndKeepingItChangesNothing() {
        let app = open("edit:demo-home", extra: ["-demo-biometrics", "approved"])
        app.buttons["edit-sign-out"].tap()
        XCTAssertTrue(app.element("confirm-sheet").waitForExistence(timeout: 10))
        app.buttons["confirm-sheet-cancel"].tap()
        XCTAssertFalse(app.element("confirm-sheet").waitForExistence(timeout: 2))
        XCTAssertTrue(app.element("edit-status").label.contains("Connected"))

        app.buttons["edit-sign-out"].tap()
        XCTAssertTrue(app.buttons["confirm-sheet-confirm"].waitForExistence(timeout: 10))
        app.buttons["confirm-sheet-confirm"].tap()
        let signedOut = NSPredicate { _, _ in app.element("edit-status").label.contains("Signed out") }
        expectation(for: signedOut, evaluatedWith: nil)
        waitForExpectations(timeout: 15)
    }

    @MainActor
    func testTheAddFormOpensFromTheFactoriesListAndSavesAfterACheck() {
        let app = XCUIApplication()
        app.launchArguments = ["-demo", "-demo-tab", "factories"]
        app.launch()
        XCTAssertTrue(app.buttons["add-factory-button"].waitForExistence(timeout: 30))
        app.buttons["add-factory-button"].tap()
        XCTAssertTrue(app.element("edit-factory").waitForExistence(timeout: 10))
        XCTAssertTrue(app.navigationBars["Add factory"].exists)
        XCTAssertFalse(app.buttons["edit-save-button"].isEnabled)
        XCTAssertFalse(app.buttons["edit-pair-again"].exists, "nothing to pair before the factory is saved")

        app.textFields["edit-name"].enterText("Studio")
        app.textFields["edit-address"].enterText("https://studio.example.org")
        app.buttons["edit-check-address"].tap()
        XCTAssertTrue(app.element("edit-probe-result").waitForExistence(timeout: 10))
        let save = app.buttons["edit-save-button"]
        XCTAssertTrue(save.isEnabled)
        save.tap()
        XCTAssertTrue(app.element("factories-root").waitForExistence(timeout: 10))
        XCTAssertTrue(app.element("factory-card-demo-4").exists || app.staticTexts["Studio"].exists)
    }

    @MainActor
    func testAccessibilityAuditOfTheEditForm() throws {
        let app = open("edit:demo-home")
        XCTAssertTrue(app.element("edit-access-company-company-acme").waitForExistence(timeout: 10))
        let findings = try auditAccessibility(app, screen: "edit-factory")
        let known = ["Text clipped", "| Cancel", "| Save", "Contrast nearly passed", "Dynamic Type font sizes"]
        let others = findings.filter { finding in !known.contains { finding.contains($0) } }
        XCTAssertTrue(others.isEmpty, others.joined(separator: "\n"))
    }
}
