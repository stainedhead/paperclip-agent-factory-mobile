import XCTest

/// The system accessibility audit of Factory home, Agents, Agent detail and the Run viewer, normal and offline.
/// There is no allow-list: every finding fails the test. CI runs these as the non-blocking audit set (ADR-080).
final class OperationsAuditUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func assertClean(_ findings: [String], file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(findings.isEmpty, findings.joined(separator: "\n"), file: file, line: line)
    }

    @MainActor
    func testAccessibilityAuditOfFactoryHome() throws {
        let app = launchFactoryHome()
        assertClean(try auditTabRoot(app, screen: "factory"))
    }

    @MainActor
    func testAccessibilityAuditOfOfflineFactoryHome() throws {
        let app = launchOperations(tab: "factory", root: "factory-root", extra: ["-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        assertClean(try auditTabRoot(app, screen: "factory-offline"))
    }

    @MainActor
    func testAccessibilityAuditOfAgents() throws {
        let app = launchAgents()
        assertClean(try auditTabRoot(app, screen: "agents"))
    }

    @MainActor
    func testAccessibilityAuditOfAgentDetail() throws {
        let app = launchOperations(
            tab: "inbox", root: "agent-detail-screen", extra: ["-demo-open", "agent:\(OpsIds.engineer1)"])
        XCTAssertTrue(app.element("agent-current-issue").waitForExistence(timeout: 30))
        assertClean(try auditDetailScreen(app, screen: "agent-detail"))
    }

    @MainActor
    func testAccessibilityAuditOfTheRunViewer() throws {
        let app = launchOperations(
            tab: "inbox", root: "run-screen", extra: ["-demo-open", "run:\(OpsIds.liveRun)"])
        XCTAssertTrue(app.element("run-log").waitForExistence(timeout: 30))
        assertClean(try auditDetailScreen(app, screen: "run"))
    }

    @MainActor
    func testAccessibilityAuditOfTheOfflineRunViewer() throws {
        let app = launchOperations(
            tab: "inbox", root: "run-screen",
            extra: ["-demo-open", "run:\(OpsIds.liveRun)", "-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        assertClean(try auditDetailScreen(app, screen: "run-offline"))
    }
}
