import XCTest

final class FirstRunUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demo-first-run"]
        app.launch()
        return app
    }

    @MainActor
    private func typeAddress(_ address: String, in app: XCUIApplication) {
        let field = app.textFields["address-field"]
        XCTAssertTrue(field.waitForExistence(timeout: 15))
        field.focus()
        field.typeText(address)
    }

    @MainActor
    func testTypingAnAddressShowsValidationFromTheSharedCore() {
        let app = launch()
        let field = app.textFields["address-field"]
        XCTAssertTrue(field.waitForExistence(timeout: 15))
        XCTAssertFalse(app.buttons["continue-button"].isEnabled)
        XCTAssertFalse(app.buttons["check-address-button"].isEnabled)

        field.focus()
        field.typeText("paperclip.example.com")
        XCTAssertTrue(app.staticTexts["address-field-issue"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["check-address-button"].isEnabled)

        field.clearAndTypeText("https://paperclip.example.com")
        XCTAssertTrue(app.staticTexts["address-field-help"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["check-address-button"].isEnabled)
        XCTAssertFalse(
            app.buttons["continue-button"].isEnabled, "Continue waits for the address check, not just a valid address")
    }

    /// Acceptance: first run, add a factory with a valid address, pair (the scripted browser approves), secure, and
    /// land on the Inbox.
    @MainActor
    func testAddAFactoryPairItAndLandOnTheInbox() {
        let app = launch()
        typeAddress("https://paperclip.example.org", in: app)

        app.buttons["check-address-button"].tap()
        let found = app.element("probe-result")
        XCTAssertTrue(found.waitForExistence(timeout: 10), "the address check never answered")
        XCTAssertTrue(found.label.contains("Server found"), found.label)
        XCTAssertTrue(found.label.contains("Paperclip 1.4.0"), found.label)
        XCTAssertEqual(app.element("step-indicator").value as? String, "1")

        let continueButton = app.buttons["continue-button"]
        XCTAssertTrue(continueButton.isEnabled)
        continueButton.tap()
        XCTAssertTrue(app.buttons["pair-button"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.element("step-indicator").value as? String, "2")

        app.buttons["pair-button"].tap()
        XCTAssertTrue(
            app.buttons["finish-button"].waitForExistence(timeout: 20), "pairing never reached the secure step")
        XCTAssertEqual(app.element("step-indicator").value as? String, "3")
        XCTAssertTrue(app.buttons["company-choice-company-acme"].exists)
        XCTAssertTrue(app.buttons["company-choice-company-side"].exists)
        app.buttons["company-choice-company-acme"].tap()
        XCTAssertTrue(app.switches["app-lock-toggle"].exists)

        app.buttons["finish-button"].tap()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab shell never appeared")
        XCTAssertTrue(app.navigationBars["Inbox"].waitForExistence(timeout: 10))
    }

    @MainActor
    func testTheDemoFactoryIsOneTapAwayAndBackFromPairingReturnsToTheAddress() {
        let app = launch()
        typeAddress("https://paperclip.example.org", in: app)
        app.buttons["check-address-button"].tap()
        XCTAssertTrue(app.element("probe-result").waitForExistence(timeout: 10))
        app.buttons["continue-button"].tap()
        XCTAssertTrue(app.buttons["back-button"].waitForExistence(timeout: 10))
        app.buttons["back-button"].tap()
        XCTAssertTrue(app.textFields["address-field"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["try-demo-button"].exists)
    }

    @MainActor
    func testAccessibilityAuditOfTheAddressStep() throws {
        let app = launch()
        XCTAssertTrue(app.textFields["address-field"].waitForExistence(timeout: 15))
        let findings = try auditAccessibility(app, screen: "first-run-address")
        // One "Text clipped" finding with no element label remains (listed in implementation-notes.md); anything
        // else, contrast above all, fails the test.
        let others = findings.filter { !$0.contains("Text clipped") }
        XCTAssertTrue(others.isEmpty, others.joined(separator: "\n"))
    }
}

extension XCUIElement {
    /// Taps until the element really has keyboard focus. On a slow or freshly booted CI simulator the first tap can
    /// land before the field is ready, and `typeText` then fails with "Neither element nor any descendant has keyboard
    /// focus".
    func focus(attempts: Int = 5) {
        for _ in 0..<attempts {
            tap()
            if hasKeyboardFocus { return }
            _ = XCUIApplication().keyboards.firstMatch.waitForExistence(timeout: 2)
            if hasKeyboardFocus { return }
        }
        XCTFail("The element never received keyboard focus after \(attempts) taps")
    }

    var hasKeyboardFocus: Bool {
        (value(forKey: "hasKeyboardFocus") as? Bool) ?? false
    }

    func clearAndTypeText(_ text: String) {
        guard let current = value as? String else { return }
        focus()
        // The first tap may land mid-text; the delete keys below remove what is before the cursor.
        coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.5)).tap()
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        typeText(text)
    }
}
