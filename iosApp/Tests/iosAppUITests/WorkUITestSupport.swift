import XCTest

/// Launch helpers for the Work list and Issue UI tests. They drive and read the screens by accessibility identifier.
enum WorkIds {
    static let retries = "pap-1204"
    static let billing = "pap-1182"
    static let refunds = "pap-1190"
}

extension XCTestCase {
    @MainActor
    func launchWork(extra: [String] = []) -> XCUIApplication {
        let app = launchOperations(tab: "work", root: "work-root", extra: extra)
        XCTAssertTrue(
            app.element("work-row-\(WorkIds.retries)").waitForExistence(timeout: 30), "the issues never loaded")
        return app
    }

    /// Opens an issue straight away (`-demo-open issue:<id>`) and waits for its content.
    @MainActor
    func launchIssue(_ id: String = WorkIds.retries, extra: [String] = []) -> XCUIApplication {
        let app = launchOperations(
            tab: "inbox", root: "issue-screen", extra: ["-demo-open", "issue:\(id)"] + extra)
        XCTAssertTrue(app.element("issue-title").waitForExistence(timeout: 30), "the issue never loaded")
        return app
    }
}

extension XCUIApplication {
    /// Scrolls the Issue screen until `identifier` is on screen and clear of the comment bar.
    @MainActor
    func scrollIssue(to identifier: String) {
        var swipes = 0
        while swipes < 8 {
            let target = element(identifier)
            let barTop = element("issue-comment-bar").frame.minY
            if target.exists && target.isHittable && target.frame.maxY < barTop - 8 { return }
            element("issue-screen").swipeUp()
            swipes += 1
        }
    }
}
