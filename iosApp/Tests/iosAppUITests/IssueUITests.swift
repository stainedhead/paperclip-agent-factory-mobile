import XCTest

/// Screen 4 on the demo graph: PAP-1204 with the agent's two questions, a plan, three runs and two files.
final class IssueUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testItShowsTheMocksHeaderTabsAndTheAgentsQuestions() {
        let app = launchIssue()
        XCTAssertEqual(app.text("issue-identifier"), "PAP-1204 \u{00B7} Billing v2")
        XCTAssertEqual(app.text("issue-title"), "Add retries to webhook ingest")
        XCTAssertEqual(app.text("issue-status"), "Blocked")
        XCTAssertEqual(app.text("issue-priority"), "High")
        XCTAssertTrue(app.text("issue-assignee-menu").contains("Engineer 1"))
        XCTAssertTrue(app.element("issue-tab-thread").isSelected)
        XCTAssertTrue(app.text("issue-comment-c-1204-1").contains("Engineer 1 \u{00B7} agent"))
        app.scrollIssue(to: "interaction-primary")
        XCTAssertTrue(app.text("interaction-kind-int-1204-questions").contains("2 QUESTIONS"))
        XCTAssertFalse(app.buttons["interaction-primary"].isEnabled, "nothing is answered yet")
    }

    @MainActor
    func testAnsweringTheTwoQuestionsSendsOnceAndClearsTheInboxCard() {
        let app = launchIssue()
        let prompts = app.promptCount()
        app.scrollIssue(to: "interaction-choice-q-policy-exponential")
        app.buttons["interaction-choice-q-policy-exponential"].tap()
        app.scrollIssue(to: "interaction-choice-q-attempts-5")
        app.buttons["interaction-choice-q-attempts-5"].tap()
        app.scrollIssue(to: "interaction-primary")
        waitUntil(app.buttons["interaction-primary"], "isEnabled == true")
        app.buttons["interaction-primary"].tap()
        waitUntil(app.element("interaction-kind-int-1204-questions"), "label CONTAINS 'ANSWERED'")
        XCTAssertFalse(app.buttons["interaction-primary"].exists, "an answered card has no buttons")
        XCTAssertEqual(app.promptCount(), prompts, "answering asks nothing of the device")

        app.goBack()
        app.selectTab("Inbox")
        XCTAssertTrue(app.element("inbox-root").waitForExistence(timeout: 15))
        XCTAssertFalse(app.element("inbox-card-\(InboxIds.question)").exists, "the answered question left the Inbox")
    }

    @MainActor
    func testACommentIsAddedAtTheEndOfTheThread() {
        let app = launchIssue()
        let prompts = app.promptCount()
        app.element("issue-comment-field").enterText("Go with exponential backoff")
        app.buttons["issue-comment-send"].tap()
        let comment = app.element("issue-comment-c3")
        XCTAssertTrue(comment.waitForExistence(timeout: 15), "the comment never appeared")
        XCTAssertTrue(comment.label.contains("Go with exponential backoff"))
        XCTAssertTrue(comment.label.hasPrefix("You"))
        XCTAssertNotEqual(
            app.element("issue-comment-field").shownText, "Go with exponential backoff", "the box is cleared")
        XCTAssertEqual(app.promptCount(), prompts, "commenting asks nothing of the device")
    }

    @MainActor
    func testTheStatusAndTheAssigneeCanBeChanged() {
        let app = launchIssue()
        let prompts = app.promptCount()
        app.buttons["issue-status-menu"].tap()
        app.buttons["Done"].tap()
        waitUntil(app.element("issue-status"), "label == 'Done'")
        app.buttons["issue-assignee-menu"].tap()
        app.buttons["CTO"].tap()
        waitUntil(app.element("issue-assignee-menu"), "label CONTAINS 'CTO'")
        XCTAssertEqual(app.promptCount(), prompts, "status and assignee ask nothing of the device")
    }

    @MainActor
    func testThePlanRunsAndFilesTabsShowTheirContent() {
        let app = launchIssue()
        app.buttons["issue-tab-plan"].tap()
        XCTAssertTrue(app.element("issue-plan").waitForExistence(timeout: 15))
        XCTAssertTrue(app.text("issue-plan-body").contains("Back off exponentially"))
        app.buttons["issue-tab-runs"].tap()
        waitUntil(app.element("issue-tab-runs"), "label == 'Runs 3'")
        app.buttons["issue-tab-files"].tap()
        waitUntil(app.element("issue-tab-files"), "label == 'Files 2'")
        XCTAssertTrue(app.text("issue-file-att-1").contains("retry-policy.md"))
        app.buttons["issue-tab-runs"].tap()
        let run = app.element("issue-run-run-1204-live")
        XCTAssertTrue(run.waitForExistence(timeout: 15))
        run.tap()
        XCTAssertTrue(app.element("run-screen").waitForExistence(timeout: 15))
    }

    @MainActor
    func testTheIssueIsReachableFromTheInboxTheRunViewerAndTheAgent() {
        let inbox = launchInbox()
        inbox.reveal(InboxIds.question)
        inbox.cardOpen(InboxIds.question).tap()
        XCTAssertTrue(inbox.element("issue-title").waitForExistence(timeout: 20))
        XCTAssertEqual(inbox.text("issue-title"), "Add retries to webhook ingest")
        inbox.terminate()

        let run = launchOperations(
            tab: "inbox", root: "run-screen", extra: ["-demo-open", "run:\(OpsIds.liveRun)"])
        run.buttons["run-issue"].tap()
        XCTAssertTrue(run.element("issue-title").waitForExistence(timeout: 20))
        XCTAssertEqual(run.text("issue-title"), "Add retries to webhook ingest")
        run.terminate()

        let agent = launchOperations(
            tab: "inbox", root: "agent-detail-screen", extra: ["-demo-open", "agent:\(OpsIds.engineer1)"])
        XCTAssertTrue(agent.element("agent-current-issue").waitForExistence(timeout: 30))
        agent.element("agent-current-issue").tap()
        XCTAssertTrue(agent.element("issue-title").waitForExistence(timeout: 20))
        XCTAssertEqual(agent.text("issue-title"), "Add retries to webhook ingest")
    }

    @MainActor
    func testOfflineTheCommentBarIsDisabledWithItsReason() {
        let app = launchIssue(extra: ["-demo-offline-after-detail"])
        XCTAssertTrue(app.element("offline-banner").waitForExistence(timeout: 30))
        XCTAssertTrue(app.element("disabled-reason").exists)
        XCTAssertFalse(app.buttons["issue-comment-send"].isEnabled)
        XCTAssertFalse(app.buttons["issue-status-menu"].isEnabled)
    }
}
