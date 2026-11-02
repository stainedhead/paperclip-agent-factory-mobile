import XCTest

/// Launch and wait helpers for the Inbox UI tests. They only drive and read the screen: every assertion is made
/// against an accessibility identifier, never a position.
enum InboxIds {
    static let decision = "decision:dec-1182"
    static let approval = "approval:ap-hire-qa"
    static let budget = "budget_alert:inc-eng-2"
    static let question = "issue_thread_interaction:int-1204-questions"
    static let failedRun = "failed_run:run-qa-1190"
    static let keysDecision = "decision:dec-1183"
}

extension XCTestCase {
    /// Launches the demo on the Inbox. The launch unlock takes the first scripted Face ID result, so the first entry
    /// of `biometrics` should be `approved`.
    @MainActor
    func launchInbox(biometrics: String = "approved", extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demo", "-demo-biometrics", biometrics, "-demo-diagnostics"] + extra
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab bar never appeared")
        app.waitUntilUnlocked()
        XCTAssertTrue(
            app.element("inbox-card-\(InboxIds.decision)").waitForExistence(timeout: 30), "the Inbox never showed cards"
        )
        return app
    }

    @MainActor
    func waitUntil(
        _ element: XCUIElement, _ format: String, timeout: TimeInterval = 15, file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let predicate = NSPredicate(format: format)
        let result = XCTWaiter().wait(
            for: [XCTNSPredicateExpectation(predicate: predicate, object: element)], timeout: timeout)
        XCTAssertEqual(result, .completed, "\(element) never satisfied \(format)", file: file, line: line)
    }
}

extension XCTestCase {
    /// Waits for the Inbox tab's badge to read `count` (the value or the label, depending on the iOS release).
    @MainActor
    func waitForInboxBadge(_ app: XCUIApplication, _ count: Int, file: StaticString = #filePath, line: UInt = #line) {
        let tab = app.tabBars.firstMatch.buttons["Inbox"]
        let badge = NSPredicate { element, _ in
            guard let button = element as? XCUIElement else { return false }
            return ((button.value as? String) ?? "").contains("\(count)") || button.label.contains("\(count)")
        }
        let result = XCTWaiter().wait(for: [XCTNSPredicateExpectation(predicate: badge, object: tab)], timeout: 30)
        XCTAssertEqual(result, .completed, "the Inbox badge never read \(count)", file: file, line: line)
    }
}

extension XCUIApplication {
    func card(_ id: String) -> XCUIElement { element("inbox-card-\(id)") }

    /// The card's text block: one button whose label is the spoken sentence.
    func cardOpen(_ id: String) -> XCUIElement { buttons["inbox-card-\(id)-open"] }

    /// Scrolls the Inbox list until the card exists, then until it is on screen.
    @MainActor
    func reveal(_ id: String) {
        let target = card(id)
        var swipes = 0
        while !(target.exists && target.isHittable) && swipes < 8 {
            element("inbox-root").swipeUp()
            swipes += 1
        }
    }

    /// Scrolls back to the header (a tap on a button under the tab bar scrolls the list).
    @MainActor
    func revealTop() {
        var swipes = 0
        while !(element("inbox-due-count").exists && element("inbox-due-count").isHittable) && swipes < 6 {
            element("inbox-root").swipeDown()
            swipes += 1
        }
    }

    @MainActor
    func promptCount() -> String? {
        let label = staticTexts["diagnostics-biometric-prompts"]
        return label.waitForExistence(timeout: 5) ? label.value as? String : nil
    }
}

extension XCTestCase {
    /// The system audit with each finding's element described (type, identifier, frame), so a finding that carries no
    /// label can still be traced. Findings under the floating tab bar are logged, not returned (the bar's own glass).
    @MainActor
    func auditInboxScreen(_ app: XCUIApplication, screen: String) throws -> [String] {
        var findings: [String] = []
        let firstTab = app.tabBars.firstMatch.buttons.firstMatch
        let barTop = firstTab.exists ? firstTab.frame.minY : CGFloat.greatestFiniteMagnitude
        try app.performAccessibilityAudit { issue in
            let element = issue.element
            let described =
                "\(element?.elementType.rawValue ?? 0) id=\(element?.identifier ?? "-") "
                + "label=\(element?.label ?? "-") frame=\(element?.frame ?? .zero)"
            if let frame = element?.frame, frame.maxY > barTop {
                print("INBOX-AUDIT-UNDER-TAB-BAR \(issue.compactDescription) | \(described)")
                return true
            }
            findings.append("\(issue.compactDescription) | \(described)")
            return true
        }
        print("INBOX-AUDIT screen=\(screen) count=\(findings.count)")
        for finding in findings { print("INBOX-AUDIT-FINDING screen=\(screen) \(finding)") }
        return findings
    }
}
