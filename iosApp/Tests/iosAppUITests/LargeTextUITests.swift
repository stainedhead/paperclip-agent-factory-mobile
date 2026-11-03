import XCTest

/// Every screen at the largest accessibility text size (XXXL). These are the blocking guard for large text (ADR-117):
/// the system accessibility audit stays visible in its own non-blocking job, and these tests assert what a person needs
/// at this size: the screen's key text is there, whole, inside the screen, and every primary action can be reached by
/// scrolling and tapped.
final class LargeTextUITests: XCTestCase {
    private static let xxxl = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// Scrolls until the element is on screen and hittable (it may start below the fold).
    @MainActor
    private func reach(_ app: XCUIApplication, _ id: String, file: StaticString = #filePath, line: UInt = #line)
        -> XCUIElement
    {
        let element = app.element(id)
        _ = element.waitForExistence(timeout: 5)
        var swipes = 0
        // Lazy rows are not built until they are near, so a missing element is scrolled towards too.
        while !(element.exists && element.isHittable) && swipes < 20 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(element.exists, "\(id) never appeared", file: file, line: line)
        XCTAssertTrue(element.isHittable, "\(id) cannot be reached by scrolling at XXXL", file: file, line: line)
        return element
    }

    /// Reached, inside the screen's width, with text that is not cut to an ellipsis.
    @MainActor
    private func assertWhole(
        _ app: XCUIApplication, _ ids: [String], file: StaticString = #filePath, line: UInt = #line
    ) {
        let width = app.windows.firstMatch.frame.width
        for id in ids {
            let element = reach(app, id, file: file, line: line)
            let frame = element.frame
            XCTAssertGreaterThan(frame.height, 0, "\(id) has no height", file: file, line: line)
            XCTAssertGreaterThanOrEqual(frame.minX, -1, "\(id) starts off the left edge", file: file, line: line)
            XCTAssertLessThanOrEqual(frame.maxX, width + 1, "\(id) runs off the right edge", file: file, line: line)
            XCTAssertFalse(element.label.contains("\u{2026}"), "\(id) reads with an ellipsis", file: file, line: line)
        }
    }

    @MainActor
    private func open(tab: String, root: String, route: String? = nil) -> XCUIApplication {
        launchOperations(
            tab: tab, root: root,
            extra: (route.map { ["-demo-open", $0] } ?? []) + ["-demo-no-auto-refresh"] + Self.xxxl)
    }

    @MainActor func testInbox() {
        let app = open(tab: "inbox", root: "inbox-root")
        assertWhole(app, ["inbox-card-\(InboxIds.decision)-title", "inbox-card-\(InboxIds.decision)-review"])
    }

    @MainActor func testFactoryHome() {
        let app = open(tab: "factory", root: "factory-root")
        assertWhole(app, ["factory-spend", "factory-run-\(OpsIds.liveRun)"])
    }

    @MainActor func testWork() {
        let app = open(tab: "work", root: "work-root")
        assertWhole(app, ["work-row-\(WorkIds.billing)", "work-row-\(WorkIds.retries)"])
    }

    @MainActor func testAgents() {
        let app = open(tab: "agents", root: "agents-root")
        assertWhole(app, ["agent-row-\(OpsIds.cto)", "agent-row-\(OpsIds.designer)"])
    }

    @MainActor func testFactories() {
        let app = open(tab: "factories", root: "factories-root")
        assertWhole(app, ["manage-factories-button", "settings-button", "factory-name-demo-home"])
    }

    @MainActor func testDecision() {
        let app = open(tab: "inbox", root: "decision-screen", route: "decision:dec-1182")
        assertWhole(app, ["decision-title", "decision-dismiss", "decision-confirm"])
    }

    @MainActor func testApproval() {
        let app = open(tab: "inbox", root: "approval-screen", route: "approval:ap-hire-qa")
        assertWhole(app, ["approval-title", "approval-approve"])
    }

    @MainActor func testBudgetIncident() {
        let app = open(tab: "inbox", root: "budget-screen", route: "budget:inc-eng-2")
        assertWhole(app, ["budget-scope", "budget-submit"])
    }

    @MainActor func testAgentDetail() {
        let app = open(tab: "inbox", root: "agent-detail-screen", route: "agent:\(OpsIds.engineer1)")
        assertWhole(app, ["agent-name", "agent-set-budget"])
    }

    @MainActor func testRunViewer() {
        let app = open(tab: "inbox", root: "run-screen", route: "run:\(OpsIds.liveRun)")
        assertWhole(app, ["run-title", "run-event-1"])
    }

    @MainActor func testIssue() {
        let app = open(tab: "inbox", root: "issue-screen", route: "issue:\(WorkIds.retries)")
        assertWhole(app, ["issue-title", "issue-comment-bar"])
    }

    @MainActor func testSettings() {
        let app = open(tab: "factories", root: "settings", route: "settings")
        assertWhole(app, ["settings-device-name", "settings-app-lock-toggle"])
    }

    @MainActor func testManageFactories() {
        let app = open(tab: "factories", root: "manage-count", route: "manage")
        assertWhole(app, ["manage-count", "add-factory-button"])
    }

    @MainActor func testEditFactory() {
        let app = open(tab: "factories", root: "edit-factory", route: "edit:demo-home")
        assertWhole(app, ["edit-name", "edit-address", "edit-save-button", "edit-cancel-button"])
    }

    @MainActor func testFirstRun() {
        let app = XCUIApplication()
        app.launchArguments = ["-demo-first-run"] + Self.xxxl
        app.launch()
        assertWhole(app, ["address-field"])
    }

    /// The text really does grow: a long title is taller at XXXL than at the default size, so the large-size checks
    /// above are looking at a scaled screen and not at the default one.
    @MainActor func testTextActuallyGrowsAtTheLargestSize() {
        let normal = launchOperations(
            tab: "inbox", root: "decision-screen", extra: ["-demo-open", "decision:dec-1182", "-demo-no-auto-refresh"])
        let normalHeight = normal.element("decision-title").frame.height
        normal.terminate()
        let large = open(tab: "inbox", root: "decision-screen", route: "decision:dec-1182")
        XCTAssertGreaterThan(large.element("decision-title").frame.height, normalHeight * 1.5)
    }
}
