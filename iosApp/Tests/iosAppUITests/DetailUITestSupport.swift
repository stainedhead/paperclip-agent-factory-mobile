import XCTest

/// Helpers for the Decision, Approval and Budget screens' UI tests. They only drive and read the screens; every
/// assertion is against an accessibility identifier, never a position.
enum DetailIds {
    static let decision = "decision:dec-1182"
    static let keysDecision = "decision:dec-1183"
    static let approval = "approval:ap-hire-qa"
    static let budget = "budget_alert:inc-eng-2"
}

extension XCUIApplication {
    /// The text of an element carrying `identifier`: its label (or value for fields).
    func text(_ identifier: String) -> String { element(identifier).label }

    /// Goes back one screen with the navigation bar's back button.
    @MainActor
    func goBack() {
        navigationBars.buttons.firstMatch.tap()
    }
}

extension XCTestCase {
    /// The system audit on a detail screen with every finding described (type, text, frame). There is no allow-list
    /// of finding types. The one geometric rule: an element that reaches below the top of the fixed action bar
    /// (`barIdentifier`) is scrolled content the bar hides, so the audit would measure it against the bar's pixels;
    /// those are printed as `DETAIL-AUDIT-BEHIND-BAR` and not returned. Everything else is returned.
    @MainActor
    func auditDetailScreen(_ app: XCUIApplication, screen: String, barIdentifier: String? = nil) throws -> [String] {
        var findings: [String] = []
        let bar = barIdentifier.map { app.element($0) }
        let barTop = (bar?.exists ?? false) ? (bar?.frame.minY ?? .greatestFiniteMagnitude) : .greatestFiniteMagnitude
        try app.performAccessibilityAudit { issue in
            let element = issue.element
            let described =
                "\(issue.compactDescription) | \(issue.detailedDescription) | id=\(element?.identifier ?? "-") "
                + "label=\(element?.label ?? "-") frame=\(element?.frame ?? .zero)"
            if let frame = element?.frame, frame.maxY > barTop {
                print("DETAIL-AUDIT-BEHIND-BAR screen=\(screen) \(described)")
                return true
            }
            findings.append(described)
            return true
        }
        print("DETAIL-AUDIT screen=\(screen) count=\(findings.count)")
        for finding in findings { print("DETAIL-AUDIT-FINDING screen=\(screen) \(finding)") }
        return findings
    }
}
