import XCTest

/// Screen 3 on the demo graph, by accessibility identifier. The values are the design mock's.
final class ApprovalUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func openHireApproval(biometrics: String = "approved", extra: [String] = []) -> XCUIApplication {
        let app = launchInbox(biometrics: biometrics, extra: extra)
        app.buttons["inbox-card-\(DetailIds.approval)-details"].tap()
        XCTAssertTrue(app.element("approval-screen").waitForExistence(timeout: 15), "the Approval screen never opened")
        XCTAssertTrue(app.element("approval-title").waitForExistence(timeout: 15), "the approval never loaded")
        return app
    }

    @MainActor
    func testItShowsTheHirePayloadLinkedWorkAndDiscussionOfTheMock() {
        let app = openHireApproval()
        XCTAssertTrue(app.text("approval-title").contains("QA engineer"), app.text("approval-title"))
        XCTAssertTrue(app.text("approval-kind-line").contains("APPROVAL"), app.text("approval-kind-line"))
        XCTAssertTrue(app.text("approval-kind-line").contains("HIRE AGENT"))
        XCTAssertEqual(app.text("status-pill"), "Pending")
        XCTAssertEqual(app.text("approval-requested-by"), "Requested by CEO")

        XCTAssertEqual(app.element("approval-row-role").value as? String, "QA")
        XCTAssertEqual(app.element("approval-row-reports-to").value as? String, "CTO")
        XCTAssertEqual(app.element("approval-row-adapter").value as? String, "claude_local")
        XCTAssertEqual(app.element("approval-row-budget").value as? String, "$150")
        XCTAssertTrue(app.text("approval-issue-pap-1190").contains("PAP-1190"))
        XCTAssertTrue(app.text("approval-comment-c-hire-1").contains("Refund regressions slipped twice"))
        XCTAssertTrue(app.text("approval-comment-c-hire-1").contains("Agent"))

        for action in ["approval-approve", "approval-reject", "approval-request-revision"] {
            XCTAssertTrue(app.buttons[action].exists, "missing \(action)")
        }
        XCTAssertTrue(app.buttons["approval-reject"].label.contains("Reject"))
    }

    @MainActor
    func testApprovingResolvesItAsksNoFaceIDAndClearsTheInboxCard() {
        let app = openHireApproval()
        app.buttons["approval-approve"].tap()
        XCTAssertTrue(app.element("approval-final").waitForExistence(timeout: 15))
        XCTAssertEqual(app.text("status-pill"), "Approved")
        XCTAssertFalse(app.buttons["approval-approve"].exists)
        XCTAssertEqual(app.promptCount(), "1", "only the launch unlock")

        app.goBack()
        waitUntil(app.card(InboxIds.approval), "exists == false")
        waitForInboxBadge(app, 2)
    }

    @MainActor
    func testRejectingNeedsANoteThenFaceIDAndACancelledPromptKeepsTheSheet() {
        let app = openHireApproval(biometrics: "approved,cancelled,approved")
        app.buttons["approval-reject"].tap()
        XCTAssertTrue(app.element("approval-note-sheet").waitForExistence(timeout: 10))

        app.buttons["approval-note-confirm"].tap()
        XCTAssertTrue(app.element("approval-note-issue").waitForExistence(timeout: 10), "an empty note is refused")
        XCTAssertEqual(app.promptCount(), "1", "no note, so no Face ID")

        app.textFields["approval-note"].enterText("Not this quarter")
        app.buttons["approval-note-confirm"].tap()
        XCTAssertTrue(app.staticTexts["confirm-destructive-issue"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.element("approval-note-sheet").exists, "the sheet stays after a cancelled prompt")
        XCTAssertEqual(app.promptCount(), "2")

        app.buttons["approval-note-confirm"].tap()
        waitUntil(app.element("approval-note-sheet"), "exists == false")
        XCTAssertTrue(app.element("approval-final").waitForExistence(timeout: 10))
        XCTAssertEqual(app.text("status-pill"), "Rejected")
        XCTAssertEqual(app.promptCount(), "3")

        app.goBack()
        waitUntil(app.card(InboxIds.approval), "exists == false")
    }

    @MainActor
    func testRequestingRevisionNeedsANoteAndOffersNoFaceID() {
        let app = openHireApproval()
        app.buttons["approval-request-revision"].tap()
        XCTAssertTrue(app.element("approval-note-sheet").waitForExistence(timeout: 10))
        app.buttons["approval-note-confirm"].tap()
        XCTAssertTrue(app.element("approval-note-issue").waitForExistence(timeout: 10))

        app.textFields["approval-note"].enterText("Lower the budget")
        app.buttons["approval-note-confirm"].tap()
        waitUntil(app.element("approval-note-sheet"), "exists == false")
        waitUntil(app.element("status-pill"), "label == 'Revision requested'")
        XCTAssertFalse(app.buttons["approval-request-revision"].exists, "offered only from pending")
        XCTAssertTrue(app.buttons["approval-approve"].exists, "approve stays possible after a revision request")
        XCTAssertEqual(app.promptCount(), "1")
    }

    @MainActor
    func testCancellingTheNoteSheetChangesNothing() {
        let app = openHireApproval()
        app.buttons["approval-reject"].tap()
        XCTAssertTrue(app.element("approval-note-sheet").waitForExistence(timeout: 10))
        app.buttons["approval-note-cancel"].tap()
        waitUntil(app.element("approval-note-sheet"), "exists == false")
        XCTAssertEqual(app.text("status-pill"), "Pending")
        XCTAssertEqual(app.promptCount(), "1")
    }

    @MainActor
    func testACommentJoinsTheDiscussion() {
        let app = openHireApproval()
        app.textFields["approval-comment-field"].enterText("Looks right to me")
        app.buttons["approval-comment-post"].tap()
        let posted = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH 'approval-comment-' AND label CONTAINS 'Looks right to me'")
        ).firstMatch
        XCTAssertTrue(posted.waitForExistence(timeout: 15), "the comment never joined the discussion")
        XCTAssertTrue(posted.label.contains("You"))
        XCTAssertEqual(
            app.textFields["approval-comment-field"].value as? String, "Ask for changes or add context",
            "the field is cleared")
    }

    @MainActor
    func testAnApprovalResolvedElsewhereShowsItsFinalStateAndNoActions() {
        let app = launchInbox(extra: ["-demo-resolved-elsewhere", "approval"])
        app.buttons["inbox-card-\(DetailIds.approval)-details"].tap()
        XCTAssertTrue(app.element("approval-final").waitForExistence(timeout: 15))
        XCTAssertEqual(app.text("status-pill"), "Approved")
        XCTAssertFalse(app.buttons["approval-approve"].exists)
        XCTAssertFalse(app.buttons["approval-reject"].exists)
        XCTAssertFalse(app.buttons["approval-request-revision"].exists)
    }

    @MainActor
    func testGoingOfflineRemovesTheActionsAndSaysWhy() {
        let app = launchInbox(extra: ["-demo-offline-after-detail"])
        app.buttons["inbox-card-\(DetailIds.approval)-details"].tap()
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 20))
        XCTAssertTrue(app.text("disabled-reason").contains("offline"))
        XCTAssertFalse(app.buttons["approval-approve"].exists)
        XCTAssertEqual(app.element("approval-row-role").value as? String, "QA", "the cached payload stays readable")
    }

    @MainActor
    func testAccessibilityAuditOfTheApprovalScreen() throws {
        let app = openHireApproval()
        let findings = try auditDetailScreen(app, screen: "approval", barIdentifier: "approval-approve")
        XCTAssertTrue(findings.isEmpty, findings.joined(separator: "\n"))
    }
}
