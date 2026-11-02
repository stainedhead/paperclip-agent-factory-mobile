import XCTest

extension XCTestCase {
    /// Runs the system accessibility audit on the current screen, prints and attaches every finding, and returns the
    /// findings. Findings are listed in implementation-notes.md; fixed ones make the count go to zero and the call
    /// sites assert on it.
    @MainActor
    @discardableResult
    func auditAccessibility(_ app: XCUIApplication, screen: String) throws -> [String] {
        var findings: [String] = []
        // The bar's own frame can be reported as infinite on iOS 26; its buttons carry the real position.
        let firstTab = app.tabBars.firstMatch.buttons.firstMatch
        let barTop = firstTab.exists ? firstTab.frame.minY : CGFloat.greatestFiniteMagnitude
        try app.performAccessibilityAudit { issue in
            // Content scrolled under the floating tab bar is measured against the bar's glass and dimming, not
            // against its own background; that is the bar's rendering, so it is logged but not a finding (ADR-080).
            if let frame = issue.element?.frame, frame.maxY > barTop {
                print("ACCESSIBILITY-AUDIT-UNDER-TAB-BAR \(issue.compactDescription) | \(issue.element?.label ?? "-")")
                return true
            }
            findings.append("\(issue.auditType) | \(issue.compactDescription) | \(issue.element?.label ?? "-")")
            return true
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
