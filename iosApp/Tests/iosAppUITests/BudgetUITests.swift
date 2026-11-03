import XCTest

/// Screen 12 on the demo graph, by accessibility identifier. The incident is the mock's: Engineer 2 hit its monthly
/// budget, $200 spent of $200.
final class BudgetUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func openSheet(biometrics: String = "approved", extra: [String] = []) -> XCUIApplication {
        let app = launchInbox(biometrics: biometrics, extra: extra)
        app.buttons["inbox-card-\(DetailIds.budget)-raise"].tap()
        XCTAssertTrue(app.element("budget-screen").waitForExistence(timeout: 15), "the sheet never opened")
        XCTAssertTrue(app.element("budget-usage").waitForExistence(timeout: 15), "the incident never loaded")
        return app
    }

    @MainActor
    private func enterAmount(_ app: XCUIApplication, _ amount: String) {
        app.buttons["budget-choice-raise"].tap()
        XCTAssertTrue(app.textFields["budget-amount"].waitForExistence(timeout: 10))
        app.textFields["budget-amount"].enterText(amount)
    }

    @MainActor
    func testItShowsTheIncidentWithSpendLimitAndBothChoices() {
        let app = openSheet()
        XCTAssertEqual(app.text("budget-scope"), "Engineer 2")
        XCTAssertEqual(app.text("status-pill"), "Hard stop reached")
        XCTAssertEqual(app.text("budget-usage"), "$200 of $200 monthly budget \u{00B7} 100% used")
        XCTAssertTrue(app.text("budget-choice-keep").contains("Keep paused"))
        XCTAssertTrue(app.text("budget-choice-raise").contains("Raise and resume"))
        XCTAssertTrue(app.text("budget-choice-raise").contains("Face ID"))
        XCTAssertFalse(app.buttons["budget-submit"].isEnabled, "nothing is chosen yet")
    }

    @MainActor
    func testARaiseBelowTheObservedSpendIsRefusedWithTheReasonBeforeAnythingIsSent() {
        let app = openSheet()
        enterAmount(app, "150")
        XCTAssertTrue(app.element("budget-amount-issue").waitForExistence(timeout: 10))
        XCTAssertTrue(app.text("budget-amount-issue").contains("higher than what has been spent"))
        XCTAssertFalse(app.buttons["budget-submit"].isEnabled)
        XCTAssertEqual(app.promptCount(), "1", "only the launch unlock")
    }

    @MainActor
    func testAValidRaiseAsksFaceIDResolvesTheIncidentAndRemovesTheInboxCard() {
        let app = openSheet(biometrics: "approved,cancelled,approved")
        enterAmount(app, "250")
        XCTAssertTrue(app.text("budget-amount-help").contains("$200"))
        waitUntil(app.buttons["budget-submit"], "isEnabled == true")
        XCTAssertTrue(
            app.buttons["budget-submit"].label.contains("Raise to $250 and resume"),
            "the amount to be sent is on the button")

        app.buttons["budget-submit"].tap()
        XCTAssertTrue(app.element("feedback-message").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("feedback-message").contains("Face ID was cancelled"))
        XCTAssertFalse(app.element("budget-outcome").exists)
        XCTAssertEqual(app.promptCount(), "2")

        app.buttons["budget-submit"].tap()
        XCTAssertTrue(app.element("budget-outcome").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("budget-outcome").contains("Budget raised"), app.text("budget-outcome"))
        XCTAssertEqual(app.promptCount(), "3")

        app.buttons["budget-close"].tap()
        waitUntil(app.element("budget-screen"), "exists == false")
        waitUntil(app.card(InboxIds.budget), "exists == false")
        waitForInboxBadge(app, 2)
    }

    @MainActor
    func testTheServersOwnReasonIsShownWhenItRefusesARaiseThatClearedTheShownSpend() {
        let app = openSheet(extra: ["-demo-budget-server-spend", "21000"])
        enterAmount(app, "205")
        waitUntil(app.buttons["budget-submit"], "isEnabled == true")
        app.buttons["budget-submit"].tap()
        XCTAssertTrue(app.element("feedback-message").waitForExistence(timeout: 15))
        XCTAssertTrue(
            app.text("feedback-message").contains("New budget must exceed current observed spend"),
            app.text("feedback-message"))
        XCTAssertFalse(app.element("budget-outcome").exists)
    }

    @MainActor
    func testKeepingPausedResolvesTheIncidentWithoutAnAmountOrFaceID() {
        let app = openSheet()
        app.buttons["budget-choice-keep"].tap()
        XCTAssertFalse(app.element("budget-amount").exists)
        app.buttons["budget-submit"].tap()
        XCTAssertTrue(app.element("budget-outcome").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("budget-outcome").contains("Kept paused"))
        XCTAssertEqual(app.promptCount(), "1")
        app.buttons["budget-close"].tap()
        waitUntil(app.card(InboxIds.budget), "exists == false")
    }

    @MainActor
    func testAnIncidentResolvedElsewhereShowsAsNoLongerActive() {
        let app = launchInbox(extra: ["-demo-resolved-elsewhere", "budget"])
        app.buttons["inbox-card-\(DetailIds.budget)-raise"].tap()
        XCTAssertTrue(app.element("budget-outcome").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("budget-outcome").contains("No longer active"))
        XCTAssertFalse(app.buttons["budget-submit"].exists)
        XCTAssertFalse(app.buttons["budget-choice-keep"].exists)
    }

    @MainActor
    func testGoingOfflineDisablesTheChoicesAndSaysWhy() {
        let app = launchInbox(extra: ["-demo-offline-after-detail"])
        app.buttons["inbox-card-\(DetailIds.budget)-raise"].tap()
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 20))
        XCTAssertTrue(app.text("disabled-reason").contains("offline"))
        XCTAssertFalse(app.buttons["budget-choice-keep"].isEnabled)
        XCTAssertFalse(app.buttons["budget-choice-raise"].isEnabled)
        XCTAssertEqual(app.text("budget-scope"), "Engineer 2", "the cached incident stays readable")
    }

    @MainActor
    func testAccessibilityAuditOfTheBudgetSheet() throws {
        let app = openSheet()
        let findings = try auditDetailScreen(app, screen: "budget")
        XCTAssertTrue(findings.isEmpty, findings.joined(separator: "\n"))
    }

    @MainActor
    func testAccessibilityAuditOfTheOfflineBudgetScreen() throws {
        let app = launchInbox(extra: ["-demo-offline-after-detail"])
        app.buttons["inbox-card-\(DetailIds.budget)-raise"].tap()
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 20))
        let findings = try auditDetailScreen(app, screen: "budget-offline")
        XCTAssertTrue(findings.isEmpty, findings.joined(separator: "\n"))
    }
}
