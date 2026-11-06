import XCTest

/// Screen 7 on the demo graph: Engineer 1's heartbeat on PAP-1204, with the mock's events and log tail.
final class RunViewerUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func openLiveRun(biometrics: String = "approved", extra: [String] = []) -> XCUIApplication {
        let app = launchFactoryHome(biometrics: biometrics, extra: extra)
        app.scroll(to: "factory-run-run-1204-live", in: "factory-root")
        XCTAssertTrue(
            app.tapOpening(app.element("factory-run-run-1204-live"), screen: app.element("run-screen")),
            "the run never opened")
        XCTAssertTrue(app.element("run-log").waitForExistence(timeout: 15), "the log never loaded")
        return app
    }

    @MainActor
    func testItShowsTheMocksHeaderEventsAndLogAndTogglesAutoScroll() {
        let app = openLiveRun()
        XCTAssertEqual(app.text("status-pill"), "Running \u{00B7} 4m 12s")
        XCTAssertEqual(app.text("run-title"), "Engineer 1 heartbeat")
        XCTAssertEqual(app.text("run-issue"), "PAP-1204 \u{00B7} Add retries to webhook ingest")
        XCTAssertEqual(app.text("run-event-1"), "0:02, Checked out PAP-1204")
        XCTAssertEqual(app.text("run-event-2"), "0:40, Opened a branch and edited 3 files")
        XCTAssertEqual(app.text("run-event-3"), "2:55, Ran tests: 2 failing")
        XCTAssertEqual(app.text("run-event-4"), "3:58, Asked you 2 questions")
        let log = app.text("run-log")
        XCTAssertTrue(log.contains("$ pnpm test webhooks"), log)
        XCTAssertTrue(log.contains("Tests: 2 failed, 14 passed"), log)
        XCTAssertTrue(log.contains("Waiting for board answer on retry policy"), log)

        XCTAssertEqual(app.text("run-autoscroll"), "auto-scroll on")
        app.buttons["run-autoscroll"].tap()
        waitUntil(app.buttons["run-autoscroll"], "label == 'auto-scroll off'")
        XCTAssertTrue(app.buttons["run-jump-to-latest"].exists)
        app.buttons["run-jump-to-latest"].tap()
        waitUntil(app.buttons["run-autoscroll"], "label == 'auto-scroll on'")
        XCTAssertFalse(app.buttons["run-jump-to-latest"].exists)
    }

    @MainActor
    func testCancellingAsksFaceIDKeepsRunningWhenCancelledThenCancelsTheRun() {
        let app = openLiveRun(biometrics: "approved,cancelled,approved")
        XCTAssertTrue(app.buttons["run-cancel"].isEnabled)
        app.buttons["run-cancel"].tap()
        XCTAssertTrue(app.element("confirm-destructive-sheet").waitForExistence(timeout: 10))
        XCTAssertTrue(app.element("confirm-destructive-faceid").exists, "the lock and Face ID are announced")
        app.buttons["confirm-destructive-confirm"].tap()
        XCTAssertTrue(app.element("feedback-message").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("feedback-message").contains("Face ID was cancelled"))
        XCTAssertTrue(app.text("status-pill").contains("Running"), "a cancelled prompt cancels nothing")
        XCTAssertEqual(app.promptCount(), "2")

        app.buttons["run-cancel"].tap()
        XCTAssertTrue(app.element("confirm-destructive-sheet").waitForExistence(timeout: 10))
        app.buttons["confirm-destructive-confirm"].tap()
        waitUntil(app.element("status-pill"), "label BEGINSWITH 'Cancelled'")
        XCTAssertFalse(app.buttons["run-cancel"].exists, "a cancelled run offers no Cancel")
        XCTAssertEqual(app.promptCount(), "3")
    }

    @MainActor
    func testOfflineKeepsTheRunReadableAndDisablesCancelWithTheReason() {
        let app = launchOperations(
            tab: "inbox", root: "run-screen",
            extra: ["-demo-open", "run:\(OpsIds.liveRun)", "-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        XCTAssertTrue(app.element("run-event-1").exists, "the cached events stay readable")
        XCTAssertFalse(app.buttons["run-cancel"].isEnabled)
        XCTAssertTrue(app.text("disabled-reason").contains("offline"))
    }
}
