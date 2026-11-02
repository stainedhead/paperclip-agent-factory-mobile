import XCTest

extension XCTestCase {
    /// Runs the system accessibility audit on the current screen, prints and attaches every finding, and returns the
    /// findings. Findings are listed in implementation-notes.md; fixed ones make the count go to zero and the call
    /// sites assert on it.
    @MainActor
    @discardableResult
    func auditAccessibility(_ app: XCUIApplication, screen: String) throws -> [String] {
        var findings: [String] = []
        try app.performAccessibilityAudit { issue in
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
