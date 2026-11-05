import XCTest

/// Screen 11: Engineer 1 from the Agents tab, then a finished run from its history.
final class AgentDetailUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testItShowsTheAgentSetsABudgetPausesWithFaceIDAndOpensAFinishedRun() {
        // Prompts in order: unlock, the budget raise (DEC-1), a cancelled pause, the pause.
        let app = launchAgents(biometrics: "approved,approved,cancelled,approved")
        app.scroll(to: "agent-row-\(OpsIds.engineer1)", in: "agents-root")
        app.element("agent-row-\(OpsIds.engineer1)").tap()
        XCTAssertTrue(app.element("agent-detail-screen").waitForExistence(timeout: 15))
        XCTAssertTrue(app.element("agent-current-issue").waitForExistence(timeout: 15))
        XCTAssertEqual(app.text("agent-name"), "Engineer 1")
        XCTAssertEqual(app.text("agent-role"), "Role: Engineer")
        XCTAssertEqual(app.text("agent-reports-to"), "Reports to: CTO")
        XCTAssertEqual(app.text("agent-current-issue"), "PAP-1204. Add retries to webhook ingest")
        XCTAssertEqual(app.text("agent-month-spend"), "$250")
        XCTAssertTrue(app.text("agent-budget").contains("$1,000"))
        XCTAssertTrue(app.text("agent-budget").contains("25% used"))

        app.buttons["agent-set-budget"].tap()
        XCTAssertTrue(app.element("agent-budget-form").waitForExistence(timeout: 10))
        app.textFields["agent-budget-amount"].enterText("1500")
        waitUntil(app.buttons["agent-budget-save"], "isEnabled == true")
        app.buttons["agent-budget-save"].tap()
        waitUntil(app.element("agent-budget"), "label CONTAINS '$1,500'", timeout: 45)

        app.buttons["agent-detail-pause"].tap()
        XCTAssertTrue(app.element("confirm-destructive-sheet").waitForExistence(timeout: 10))
        app.buttons["confirm-destructive-confirm"].tap()
        XCTAssertTrue(app.element("feedback-message").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("feedback-message").contains("Face ID was cancelled"))
        app.buttons["agent-detail-pause"].tap()
        XCTAssertTrue(app.element("confirm-destructive-sheet").waitForExistence(timeout: 10))
        app.buttons["confirm-destructive-confirm"].tap()
        XCTAssertTrue(app.buttons["agent-detail-resume"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("status-pill").contains("Paused"))

        app.scroll(to: "agent-run-\(OpsIds.doneRun)", in: "agent-detail-screen")
        app.element("agent-run-\(OpsIds.doneRun)").tap()
        XCTAssertTrue(app.element("run-screen").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("status-pill").contains("Succeeded"))
        XCTAssertFalse(app.buttons["run-cancel"].exists, "a finished run cannot be cancelled")
    }
}
