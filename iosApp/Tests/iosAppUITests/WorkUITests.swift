import XCTest

/// Screen 10 on the demo graph. There is no mock: the demo issues are the mock's (Billing v2 and PAP-1204).
final class WorkUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testItListsTheOpenIssuesAndTheBlockedFilterNarrowsThemToTheMocksIssue() {
        let app = launchWork()
        XCTAssertEqual(app.text("work-count"), "9 issues")
        XCTAssertTrue(
            app.text("work-row-\(WorkIds.retries)").contains("PAP-1204. Add retries to webhook ingest. Blocked"))
        app.buttons["work-filter-blocked"].tap()
        waitUntil(app.element("work-count"), "label == '1 issue'")
        XCTAssertTrue(app.element("work-row-\(WorkIds.retries)").exists)
        XCTAssertFalse(app.element("work-row-\(WorkIds.billing)").exists)
        app.buttons["work-filter-allOpen"].tap()
        waitUntil(app.element("work-count"), "label == '9 issues'")
    }

    @MainActor
    func testSearchingFindsAnIssueAndClearingShowsTheFilterAgain() {
        let app = launchWork()
        app.textFields["work-search"].enterText("refund")
        app.textFields["work-search"].typeText("\n")
        waitUntil(app.element("work-count"), "label == '1 issue'")
        XCTAssertTrue(app.element("work-row-\(WorkIds.refunds)").exists)
        app.textFields["work-search"].clearAndTypeText("zzz")
        app.textFields["work-search"].typeText("\n")
        XCTAssertTrue(app.element("empty-state").waitForExistence(timeout: 15))
    }

    @MainActor
    func testCreatingAnIssueOpensItWithTheTitleAndPriority() {
        let app = launchWork(extra: ["-demo-biometrics", "approved"])
        let prompts = app.promptCount()
        app.buttons["work-create"].tap()
        XCTAssertTrue(app.element("work-create-form").waitForExistence(timeout: 10))
        app.textFields["work-create-title"].enterText("Rotate the webhook secret")
        app.buttons["High"].tap()
        app.buttons["work-create-save"].tap()
        XCTAssertTrue(app.element("issue-screen").waitForExistence(timeout: 20), "the new issue never opened")
        waitUntil(app.element("issue-title"), "label == 'Rotate the webhook secret'")
        XCTAssertEqual(app.text("issue-priority"), "High")
        XCTAssertEqual(app.promptCount(), prompts, "creating an issue asks nothing of the device")
    }

    @MainActor
    func testATappedRowOpensTheIssueAndBackKeepsTheFilter() {
        let app = launchWork()
        app.buttons["work-filter-blocked"].tap()
        waitUntil(app.element("work-count"), "label == '1 issue'")
        app.element("work-row-\(WorkIds.retries)").tap()
        XCTAssertTrue(app.element("issue-screen").waitForExistence(timeout: 15))
        XCTAssertEqual(app.text("issue-title"), "Add retries to webhook ingest")
        app.goBack()
        XCTAssertTrue(app.element("work-root").waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["work-filter-blocked"].isSelected, "the filter is kept")
    }

    @MainActor
    func testGoingOfflineKeepsTheRowsAndHidesNothingButSaysSo() {
        let app = launchOperations(tab: "work", root: "work-root", extra: ["-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        XCTAssertTrue(app.element("work-row-\(WorkIds.retries)").exists, "the saved rows stay")
    }
}
