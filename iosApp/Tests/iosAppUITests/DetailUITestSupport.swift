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
    /// The system audit on a detail screen. No finding is set aside because of where it is: the fixed action bar's own
    /// buttons are audited like everything else (see `AuditOwnership`).
    @MainActor
    func auditDetailScreen(_ app: XCUIApplication, screen: String) throws -> [String] {
        try auditScreen(app, screen: screen)
    }
}
