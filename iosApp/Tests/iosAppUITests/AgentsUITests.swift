import XCTest

/// Screen 6 on the demo graph. The agents are the mock's: QA in error, Engineer 2 paused by its budget, CTO,
/// Designer and Engineer 1 running, CEO idle.
final class AgentsUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testItGroupsTheAgentsAndOffersEachOneItsButtonsByRole() {
        let app = launchAgents()
        XCTAssertEqual(app.text("agents-count"), "6 agents")
        XCTAssertTrue(app.element("agents-section-needsAttention").exists)
        XCTAssertEqual(app.text("agent-row-\(OpsIds.qaAgent)"), "QA agent. Error \u{00B7} last heartbeat 2m ago")
        XCTAssertTrue(app.buttons["agent-\(OpsIds.qaAgent)-runNow"].exists)
        XCTAssertEqual(app.text("agent-row-\(OpsIds.engineer2)"), "Engineer 2. Paused by budget hard stop")
        XCTAssertFalse(app.buttons["agent-\(OpsIds.engineer2)-resume"].exists, "a budget pause is lifted by the budget")
        XCTAssertTrue(app.buttons["agent-\(OpsIds.engineer2)-budget"].exists)

        app.scroll(to: "agent-row-\(OpsIds.cto)", in: "agents-root")
        XCTAssertEqual(app.text("agent-row-\(OpsIds.cto)"), "CTO. Running \u{00B7} PAP-1182 \u{00B7} 1m")
        for button in ["viewRun", "budget", "pause"] {
            XCTAssertTrue(app.buttons["agent-\(OpsIds.cto)-\(button)"].exists, button)
        }
        XCTAssertFalse(app.buttons["agent-\(OpsIds.cto)-runNow"].exists, "View run replaces Run now")

        app.scroll(to: "agent-row-\(OpsIds.ceo)", in: "agents-root")
        XCTAssertEqual(
            app.text("agent-row-\(OpsIds.engineer1)"), "Engineer 1. Running \u{00B7} PAP-1204 \u{00B7} 4m")
        XCTAssertEqual(app.text("agent-row-\(OpsIds.designer)"), "Designer. Running \u{00B7} PAP-1199 \u{00B7} 12m")
        XCTAssertEqual(app.text("agent-row-\(OpsIds.ceo)"), "CEO. Idle \u{00B7} last heartbeat 2m ago")

        // The Side Project company is an operator's: no budgets, but the same pause.
        app.selectTab("Factories")
        let side = app.buttons["company-chip-demo-home-company-side"]
        XCTAssertTrue(side.waitForExistence(timeout: 15))
        side.tap()
        waitUntil(side, "isSelected == true")
        app.selectTab("Agents")
        XCTAssertTrue(app.element("agent-row-\(OpsIds.cto)").waitForExistence(timeout: 15))
        XCTAssertFalse(app.buttons["agent-\(OpsIds.cto)-budget"].exists, "setting budgets is the owner's")
        XCTAssertTrue(app.buttons["agent-\(OpsIds.cto)-pause"].exists)
    }

    @MainActor
    func testPausingAsksFaceIDStaysRunningWhenCancelledThenPausesAndResumes() {
        let app = launchAgents(biometrics: "approved,cancelled,approved")
        app.scroll(to: "agent-\(OpsIds.cto)-pause", in: "agents-root")
        app.buttons["agent-\(OpsIds.cto)-pause"].tap()
        XCTAssertTrue(app.element("confirm-destructive-sheet").waitForExistence(timeout: 10))
        XCTAssertTrue(app.element("confirm-destructive-faceid").exists)
        app.buttons["confirm-destructive-confirm"].tap()
        XCTAssertTrue(app.element("feedback-message").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("feedback-message").contains("Face ID was cancelled"))
        XCTAssertEqual(app.promptCount(), "2")
        XCTAssertTrue(app.text("agent-row-\(OpsIds.cto)").contains("Running"), "still running")

        app.scroll(to: "agent-\(OpsIds.cto)-pause", in: "agents-root")
        app.buttons["agent-\(OpsIds.cto)-pause"].tap()
        XCTAssertTrue(app.element("confirm-destructive-sheet").waitForExistence(timeout: 10))
        app.buttons["confirm-destructive-confirm"].tap()
        XCTAssertTrue(app.buttons["agent-\(OpsIds.cto)-resume"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("agent-row-\(OpsIds.cto)").contains("Paused"))
        XCTAssertEqual(app.promptCount(), "3")

        app.buttons["agent-\(OpsIds.cto)-resume"].tap()
        XCTAssertTrue(app.buttons["agent-\(OpsIds.cto)-pause"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("agent-row-\(OpsIds.cto)").contains("Idle"))
        XCTAssertEqual(app.promptCount(), "3", "resume is not a Face ID action")
    }

    @MainActor
    func testRunNowStartsARunSoTheAgentMovesToRunningWithAViewRunButton() {
        let app = launchAgents()
        app.buttons["agent-\(OpsIds.qaAgent)-runNow"].tap()
        XCTAssertTrue(app.buttons["agent-\(OpsIds.qaAgent)-viewRun"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("agent-row-\(OpsIds.qaAgent)").contains("Running"))
        XCTAssertEqual(app.promptCount(), "1", "run now asks nothing of the device")
        app.buttons["agent-\(OpsIds.qaAgent)-viewRun"].tap()
        XCTAssertTrue(app.element("run-screen").waitForExistence(timeout: 15))
    }
}
