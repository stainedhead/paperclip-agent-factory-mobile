import XCTest

/// Screen 5 on the demo graph, by accessibility identifier. The numbers are the mock's.
final class FactoryHomeUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testItShowsTheMocksSpendTilesAndRunningNowAndOpensARun() {
        let app = launchFactoryHome()
        XCTAssertEqual(app.text("factory-spend"), "$1,240 of $2,000")
        XCTAssertEqual(app.text("factory-spend-percent"), "62% of the monthly budget used")
        XCTAssertEqual(app.text("factory-window-last5Hours"), "5h $38")
        XCTAssertEqual(app.text("factory-window-last24Hours"), "24h $212")
        XCTAssertEqual(app.text("factory-window-last7Days"), "7d $1,015")

        XCTAssertEqual(app.text("factory-tile-agents"), "Agents. 3 running. 1 error \u{00B7} 1 paused")
        XCTAssertEqual(app.text("factory-tile-work"), "Work. 18 open. 2 blocked \u{00B7} 5 in progress")
        XCTAssertEqual(app.text("factory-tile-approvals"), "Approvals. 2 pending")
        XCTAssertEqual(app.text("factory-tile-budget"), "Budget incidents. 1 open")

        app.scroll(to: "factory-run-run-design-live", in: "factory-root")
        XCTAssertEqual(app.text("factory-run-run-1204-live"), "Engineer 1 \u{00B7} PAP-1204, Running, 4m")
        XCTAssertEqual(app.text("factory-run-run-cto-live"), "CTO \u{00B7} PAP-1182, Running, 1m")
        XCTAssertEqual(app.text("factory-run-run-design-live"), "Designer \u{00B7} PAP-1199, Running, 12m")

        app.element("factory-run-run-cto-live").tap()
        XCTAssertTrue(app.element("run-screen").waitForExistence(timeout: 15), "the run never opened")
        XCTAssertTrue(app.text("run-issue").contains("PAP-1182"))
        app.goBack()
        XCTAssertTrue(app.element("factory-root").waitForExistence(timeout: 10))
    }

    @MainActor
    func testGoingOfflineKeepsTheLastNumbersAndSaysSo() {
        let app = launchOperations(
            tab: "factory", root: "factory-root", extra: ["-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        XCTAssertEqual(app.text("factory-spend"), "$1,240 of $2,000", "the cached numbers stay readable")
        XCTAssertTrue(app.text("offline-banner").contains("Offline"))
    }
}
