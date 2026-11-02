import XCTest

/// Screen 2 on the demo graph, by accessibility identifier. The values are the design mock's.
final class DecisionUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func openBillingDecision(biometrics: String = "approved", extra: [String] = []) -> XCUIApplication {
        let app = launchInbox(biometrics: biometrics, extra: extra)
        app.buttons["inbox-card-\(DetailIds.decision)-review"].tap()
        XCTAssertTrue(app.element("decision-screen").waitForExistence(timeout: 15), "the Decision screen never opened")
        XCTAssertTrue(app.element("decision-option-ship").waitForExistence(timeout: 15), "the options never loaded")
        return app
    }

    @MainActor
    func testItShowsTheQuestionTheAgentAndEveryOptionWithItsEffectsBeforeChoosing() {
        let app = openBillingDecision()

        XCTAssertEqual(app.text("decision-title"), "Ship the billing migration to production?")
        XCTAssertTrue(app.text("decision-kind-line").contains("DECISION"), app.text("decision-kind-line"))
        XCTAssertTrue(app.text("decision-kind-line").contains("DECIDE BY TODAY"))
        XCTAssertTrue(app.text("decision-meta").contains("Proposed by CTO"))
        XCTAssertTrue(app.text("decision-meta").contains("PAP-1182"))
        XCTAssertTrue(app.text("decision-body").contains("Staging checks pass."))

        let ship = app.text("decision-option-ship")
        XCTAssertTrue(ship.contains("Ship tonight behind a feature flag"), ship)
        XCTAssertTrue(ship.contains("Sets the issue status to in progress"), ship)
        XCTAssertTrue(ship.contains("Assigns the issue to DevOps"), ship)
        XCTAssertTrue(ship.contains("Posts a comment"), ship)
        let wait = app.text("decision-option-wait")
        XCTAssertTrue(wait.contains("Wait for QA sign-off") && wait.contains("QA regression on refunds"), wait)
        let cancel = app.text("decision-option-cancel")
        XCTAssertTrue(cancel.contains("Cancel the migration"), cancel)
        XCTAssertTrue(cancel.contains("Destructive") && cancel.contains("Face ID"), cancel)
        XCTAssertTrue(cancel.contains("Skipped if the issue changed"), cancel)

        XCTAssertFalse(app.buttons["decision-confirm"].isEnabled, "nothing is chosen yet")
        XCTAssertTrue(app.buttons["decision-dismiss"].isEnabled)
        XCTAssertTrue(app.buttons["decision-snooze"].exists, "opened from an Inbox card, so Snooze is offered")
        XCTAssertTrue(app.element("decision-input-note").exists)
    }

    @MainActor
    func testChoosingAndConfirmingANonDestructiveOptionShowsEachEffectDoneAndClearsTheInboxCard() {
        let app = openBillingDecision()
        app.buttons["decision-option-ship"].tap()
        XCTAssertTrue(app.buttons["decision-option-ship"].isSelected)
        XCTAssertTrue(app.buttons["decision-confirm"].isEnabled)
        XCTAssertEqual(app.promptCount(), "1", "only the launch unlock: a plain option asks no Face ID")

        app.buttons["decision-confirm"].tap()
        XCTAssertTrue(app.element("decision-final").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("decision-final").contains("Ship tonight behind a feature flag"))
        for index in 0..<3 {
            XCTAssertTrue(app.element("decision-result-\(index)").exists)
            XCTAssertTrue(app.text("decision-result-\(index)").contains("Done"), app.text("decision-result-\(index)"))
        }
        XCTAssertFalse(app.element("decision-option-ship").exists, "no options once it is decided")
        XCTAssertFalse(app.buttons["decision-confirm"].exists)
        XCTAssertEqual(app.promptCount(), "1")

        app.goBack()
        waitUntil(app.card(InboxIds.decision), "exists == false")
        waitForInboxBadge(app, 2)
    }

    @MainActor
    func testADestructiveOptionKeepsEverythingWhenFaceIDIsCancelledAndRunsWhenApproved() {
        let app = openBillingDecision(biometrics: "approved,cancelled,approved")
        app.buttons["decision-option-cancel"].tap()
        XCTAssertTrue(app.buttons["decision-confirm"].label.contains("Face ID"), app.buttons["decision-confirm"].label)

        app.buttons["decision-confirm"].tap()
        XCTAssertTrue(app.element("feedback-message").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("feedback-message").contains("Face ID was cancelled"), app.text("feedback-message"))
        XCTAssertEqual(app.promptCount(), "2")
        XCTAssertTrue(app.element("decision-option-cancel").exists, "nothing changed: the options are still there")
        XCTAssertFalse(app.element("decision-final").exists)

        app.buttons["decision-confirm"].tap()
        XCTAssertTrue(app.element("decision-final").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("decision-final").contains("Cancel the migration"))
        XCTAssertTrue(app.element("decision-result-0").exists)
        XCTAssertEqual(app.promptCount(), "3")

        app.goBack()
        waitUntil(app.card(InboxIds.decision), "exists == false")
        app.revealTop()
        waitUntil(app.element("inbox-due-count"), "label == '2 new or due today'")
    }

    @MainActor
    func testDismissingTheDecisionShowsItAsDismissedAndRemovesTheCard() {
        let app = openBillingDecision()
        app.buttons["decision-dismiss"].tap()
        XCTAssertTrue(app.element("decision-final").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("decision-final").contains("dismissed"), app.text("decision-final"))
        app.goBack()
        waitUntil(app.card(InboxIds.decision), "exists == false")
    }

    @MainActor
    func testARequiredAnswerIsRefusedWhenEmptyWithTheReasonUnderTheField() {
        let app = launchInbox()
        app.reveal(DetailIds.keysDecision)
        app.cardOpen(DetailIds.keysDecision).tap()
        XCTAssertTrue(app.element("decision-option-yes").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("decision-input-reason-help").contains("0 / 40"), "the limit is shown")
        app.buttons["decision-option-yes"].tap()
        app.buttons["decision-confirm"].tap()

        XCTAssertTrue(app.element("decision-input-reason-issue").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("decision-input-reason-issue").contains("required"))
        XCTAssertFalse(app.element("decision-final").exists)

        app.textFields["decision-input-reason"].enterText("Quarter end")
        app.buttons["decision-confirm"].tap()
        XCTAssertTrue(app.element("decision-final").waitForExistence(timeout: 15))
    }

    @MainActor
    func testSnoozeInTheTopBarClosesTheScreenAndRemovesTheCard() {
        let app = openBillingDecision()
        app.buttons["decision-snooze"].tap()
        let tomorrow = app.buttons["decision-snooze-tomorrow"]
        XCTAssertTrue(tomorrow.waitForExistence(timeout: 10))
        for preset in ["oneHour", "tonight", "nextWeek"] {
            XCTAssertTrue(app.buttons["decision-snooze-\(preset)"].exists, "missing preset \(preset)")
        }
        tomorrow.tap()
        waitUntil(app.element("decision-screen"), "exists == false")
        waitUntil(app.card(InboxIds.decision), "exists == false")
    }

    @MainActor
    func testADecisionResolvedElsewhereShowsItsFinalStateAndNoOptions() {
        let app = launchInbox(extra: ["-demo-resolved-elsewhere", "decision"])
        app.buttons["inbox-card-\(DetailIds.decision)-review"].tap()
        XCTAssertTrue(app.element("decision-final").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("decision-final").contains("Wait for QA sign-off"), app.text("decision-final"))
        XCTAssertFalse(app.element("decision-option-ship").exists)
        XCTAssertFalse(app.buttons["decision-confirm"].exists)
        XCTAssertFalse(app.buttons["decision-dismiss"].exists)
    }

    @MainActor
    func testGoingOfflineDisablesTheActionsAndSaysWhy() {
        let app = launchInbox(extra: ["-demo-offline-after-detail"])
        app.buttons["inbox-card-\(DetailIds.decision)-review"].tap()
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 20))
        XCTAssertTrue(app.element("disabled-reason").exists)
        XCTAssertTrue(app.text("disabled-reason").contains("offline"))
        XCTAssertFalse(app.buttons["decision-confirm"].isEnabled)
        XCTAssertFalse(app.buttons["decision-dismiss"].isEnabled)
        XCTAssertTrue(app.element("decision-option-ship").exists, "the cached options stay readable")
    }

    @MainActor
    func testAccessibilityAuditOfTheDecisionScreen() throws {
        let app = openBillingDecision()
        let findings = try auditDetailScreen(app, screen: "decision", barIdentifier: "decision-dismiss")
        XCTAssertTrue(findings.isEmpty, findings.joined(separator: "\n"))
    }
}
