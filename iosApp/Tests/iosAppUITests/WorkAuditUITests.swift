import XCTest

/// The system accessibility audit of the Work list and the Issue screen, normal and offline. No allow-list and no
/// expected failure: every finding fails the test. CI runs these as the non-blocking audit set (ADR-080).
final class WorkAuditUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testAccessibilityAuditOfTheWorkList() throws {
        let app = launchWork()
        XCTAssertEqual(try auditTabRoot(app, screen: "work"), [])
    }

    @MainActor
    func testAccessibilityAuditOfTheOfflineWorkList() throws {
        let app = launchOperations(tab: "work", root: "work-root", extra: ["-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        XCTAssertEqual(try auditTabRoot(app, screen: "work-offline"), [])
    }

    @MainActor
    func testAccessibilityAuditOfTheIssue() throws {
        let app = launchIssue()
        XCTAssertEqual(try auditDetailScreen(app, screen: "issue", barIdentifier: "issue-comment-bar"), [])
    }

    @MainActor
    func testAccessibilityAuditOfTheOfflineIssue() throws {
        let app = launchIssue(extra: ["-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        XCTAssertEqual(try auditDetailScreen(app, screen: "issue-offline", barIdentifier: "issue-comment-bar"), [])
    }
}
