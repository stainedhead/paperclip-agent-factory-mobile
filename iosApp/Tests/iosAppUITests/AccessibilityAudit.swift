import XCTest

extension XCTestCase {
    /// Runs the system accessibility audit on the current screen, prints and attaches every finding, and returns the
    /// findings. Findings are listed in implementation-notes.md; fixed ones make the count go to zero and the call
    /// sites assert on it.
    @MainActor
    @discardableResult
    func auditAccessibility(_ app: XCUIApplication, screen: String) throws -> [String] {
        var findings: [String] = []
        // The audit samples a screenshot. On a slow runner it can catch cards still fading in and report black text on
        // white as a contrast failure, so an audit with a contrast finding is repeated (up to three times) after the
        // screen has settled; a real contrast problem shows up every time and still fails the test.
        for attempt in 1...3 {
            findings = []
            try app.performAccessibilityAudit { issue in
                findings.append("\(issue.auditType) | \(issue.compactDescription) | \(issue.element?.label ?? "-")")
                return true
            }
            if !findings.contains(where: { $0.contains("Contrast failed") }) { break }
            if attempt < 3 { Thread.sleep(forTimeInterval: 2) }
        }
        let attachment = XCTAttachment(string: findings.joined(separator: "\n"))
        attachment.name = "accessibility-audit-\(screen)"
        attachment.lifetime = .keepAlways
        add(attachment)
        print("ACCESSIBILITY-AUDIT screen=\(screen) count=\(findings.count)")
        for finding in findings { print("ACCESSIBILITY-AUDIT-FINDING screen=\(screen) \(finding)") }
        return findings
    }
}

extension XCUIApplication {
    /// Whatever element carries this accessibility identifier, whatever type SwiftUI made it.
    func element(_ identifier: String) -> XCUIElement {
        descendants(matching: .any).matching(identifier: identifier).firstMatch
    }
}

extension XCUIApplication {
    /// Waits until the lock screen is gone. The demo graph unlocks itself once when the lock appears; on a slow runner
    /// the screen has been seen to stay up, so after a grace period the person's own route (the Unlock button) is used.
    @MainActor
    func waitUntilUnlocked(file: StaticString = #filePath, line: UInt = #line) {
        let lock = element("lock-screen")
        guard lock.waitForExistence(timeout: 3) else { return }
        let cleared = NSPredicate(format: "exists == false")
        let grace = XCTWaiter().wait(for: [XCTNSPredicateExpectation(predicate: cleared, object: lock)], timeout: 8)
        if grace != .completed, buttons["unlock-button"].exists { buttons["unlock-button"].tap() }
        let result = XCTWaiter().wait(for: [XCTNSPredicateExpectation(predicate: cleared, object: lock)], timeout: 20)
        XCTAssertEqual(result, .completed, "The lock screen never cleared", file: file, line: line)
    }
}
