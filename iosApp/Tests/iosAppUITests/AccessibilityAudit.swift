import XCTest

/// The one place an accessibility audit is run and its findings are sorted.
///
/// Nothing the audit reports is dropped. A finding is either returned (and the calling test fails on it) or it is on
/// a documented system-owned element and is counted, printed and attached. The only system-owned elements are the
/// buttons of the system tab bar (the bar is UIKit's own glass and is not drawn by this app). Content that scrolls
/// under the bar, and everything in the app's own bars (the Cancel run, Approve, Confirm and comment bars), is never
/// set aside: the finding is returned, and the audit set, which does not block CI (ADR-080), carries it.
enum AuditOwnership {
    /// The buttons of the system tab bar as (identifier, label) pairs, read when the audit runs.
    @MainActor
    static func tabBarButtons(in app: XCUIApplication) -> Set<String> {
        let buttons = app.tabBars.firstMatch.buttons.allElementsBoundByIndex
        return Set(buttons.map { "\($0.identifier)|\($0.label)" })
    }

    @MainActor
    static func isSystemTabBarButton(_ element: XCUIElement?, among buttons: Set<String>) -> Bool {
        guard let element, element.elementType == .button else { return false }
        return buttons.contains("\(element.identifier)|\(element.label)")
    }
}

extension XCTestCase {
    /// Runs the system accessibility audit on the current screen. Every finding is described (type, text, element
    /// type, identifier, label, frame), printed and attached; the ones on the system tab bar are counted separately as
    /// `system-owned`. Returns the findings that count; call sites assert it is empty.
    @MainActor
    func auditScreen(_ app: XCUIApplication, screen: String) throws -> [String] {
        var findings: [String] = []
        var systemOwned: [String] = []
        var untraceable = false
        let barButtons = AuditOwnership.tabBarButtons(in: app)
        try app.performAccessibilityAudit { issue in
            let element = issue.element
            let described =
                "\(issue.auditType.rawValue) | \(issue.compactDescription) | \(issue.detailedDescription) | "
                + "type=\(element?.elementType.rawValue ?? 0) id=\(element?.identifier ?? "-") "
                + "label=\(element?.label ?? "-") frame=\(element?.frame ?? .zero)"
            if element == nil || element?.frame == .zero {
                // An issue with no element cannot be traced from its description alone: print everything the issue has.
                print("ACCESSIBILITY-AUDIT-UNTRACEABLE screen=\(screen) issue=\(issue.debugDescription)")
                print("ACCESSIBILITY-AUDIT-UNTRACEABLE-ELEMENT \(element?.debugDescription ?? "nil")")
                untraceable = true
            }
            if AuditOwnership.isSystemTabBarButton(element, among: barButtons) {
                systemOwned.append(described)
            } else {
                findings.append(described)
            }
            return true
        }
        if untraceable { print("ACCESSIBILITY-AUDIT-HIERARCHY screen=\(screen)\n\(app.debugDescription)") }
        let report = (["findings=\(findings.count)"] + findings + ["system-owned=\(systemOwned.count)"] + systemOwned)
        let attachment = XCTAttachment(string: report.joined(separator: "\n"))
        attachment.name = "accessibility-audit-\(screen)"
        attachment.lifetime = .keepAlways
        add(attachment)
        print("ACCESSIBILITY-AUDIT screen=\(screen) findings=\(findings.count) system-owned=\(systemOwned.count)")
        for finding in findings { print("ACCESSIBILITY-AUDIT-FINDING screen=\(screen) \(finding)") }
        for item in systemOwned { print("ACCESSIBILITY-AUDIT-SYSTEM-OWNED screen=\(screen) \(item)") }
        return findings
    }

    @MainActor
    func auditAccessibility(_ app: XCUIApplication, screen: String) throws -> [String] {
        try auditScreen(app, screen: screen)
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
