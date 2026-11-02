import XCTest

/// Screen 8 on the demo graph: the Factories tab of the mock, by accessibility identifier.
final class FactoriesUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func openFactories() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demo", "-demo-biometrics", "approved", "-demo-diagnostics"]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab bar never appeared")
        app.tabBars.firstMatch.buttons["Factories"].tap()
        XCTAssertTrue(app.navigationBars["Factories"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.element("factory-card-demo-home").waitForExistence(timeout: 10))
        return app
    }

    @MainActor
    func testTheTabListsTheThreeFactoriesOfTheMockWithStatusExpiryAndChips() {
        let app = openFactories()

        XCTAssertEqual(app.staticTexts["factory-name-demo-home"].label, "Home factory")
        XCTAssertEqual(app.staticTexts["factory-status-demo-home"].label, "Connected")
        XCTAssertEqual(app.staticTexts["factory-expiry-demo-home"].label, "Board key expires in 23 days")
        XCTAssertTrue(app.buttons["company-chip-demo-home-company-acme"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons["company-chip-demo-home-company-acme"].label, "Acme Labs")
        XCTAssertEqual(app.buttons["company-chip-demo-home-company-side"].label, "Side Project Co")

        XCTAssertEqual(app.staticTexts["factory-name-demo-cloud"].label, "Cloud factory")
        XCTAssertEqual(app.staticTexts["factory-status-demo-cloud"].label, "Key expired")
        XCTAssertTrue(app.buttons["pair-again-demo-cloud"].exists)
        XCTAssertFalse(app.buttons["pair-again-demo-home"].exists)

        XCTAssertEqual(app.staticTexts["factory-name-demo-test"].label, "Test factory")
        XCTAssertEqual(app.staticTexts["factory-status-demo-test"].label, "Signed out")
        XCTAssertTrue(app.buttons["add-factory-button"].exists)
        XCTAssertTrue(app.element("updated-label").waitForExistence(timeout: 10))
    }

    @MainActor
    func testSwitchingCompanyMarksTheChosenChip() {
        let app = openFactories()
        let acme = app.buttons["company-chip-demo-home-company-acme"]
        let side = app.buttons["company-chip-demo-home-company-side"]
        XCTAssertTrue(acme.waitForExistence(timeout: 10))
        XCTAssertTrue(acme.isSelected, "Acme Labs is the active company")
        XCTAssertFalse(side.isSelected)

        side.tap()
        let selected = NSPredicate(format: "isSelected == true")
        expectation(for: selected, evaluatedWith: side)
        waitForExpectations(timeout: 10)
        XCTAssertFalse(acme.isSelected)
    }

    @MainActor
    func testAccessibilityAuditOfTheFactoriesTab() throws {
        let app = openFactories()
        XCTAssertTrue(app.buttons["company-chip-demo-home-company-acme"].waitForExistence(timeout: 10))
        let findings = try auditAccessibility(app, screen: "factories")
        // Listed in implementation-notes.md: the system nav-bar "Edit" button (glass material), "Add a factory" half
        // under the floating tab bar, and "Text clipped" on the address lines. Anything else fails.
        let others = findings.filter { finding in
            !["| Edit", "Add a factory", "Text clipped"].contains { finding.contains($0) }
        }
        XCTAssertTrue(others.isEmpty, others.joined(separator: "\n"))
    }
}
