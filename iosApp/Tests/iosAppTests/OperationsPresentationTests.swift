import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// The words and colours of Factory home, Agents, Agent detail and the Run viewer: lookups and joins over the shared
/// UI models, with no rule of their own.
@MainActor
struct OperationsPresentationTests {
    private let reference = Date(timeIntervalSince1970: 1_800_000_000)

    private func millis(_ secondsAgo: Double) -> Int64 {
        Int64((reference.timeIntervalSince1970 - secondsAgo) * 1000)
    }

    private func run(
        status: RunStatusUi = .running, startedSecondsAgo: Double? = 252, finishedSecondsAgo: Double? = nil,
        identifier: String? = "PAP-1204", title: String? = "Add retries to webhook ingest"
    ) -> RunRowUi {
        RunRowUi(
            runId: "run-1", agentName: "Engineer 1", issueId: "pap-1204", status: status,
            startedAtEpochMillis: startedSecondsAgo.map { KotlinLong(value: millis($0)) },
            createdAtEpochMillis: millis(startedSecondsAgo ?? 0),
            finishedAtEpochMillis: finishedSecondsAgo.map { KotlinLong(value: millis($0)) },
            issueIdentifier: identifier, issueTitle: title)
    }

    private func agent(
        name: String = "CTO", status: AgentStatusUi = .running, lifecycle: String? = nil, budgetPaused: Bool = false,
        heartbeatSecondsAgo: Double? = 120, current: RunRowUi? = nil
    ) -> AgentRowUi {
        AgentRowUi(
            id: "agent-1", name: name, role: "cto", status: status, lifecycleState: lifecycle,
            isBudgetPaused: budgetPaused, hasBudget: true, budgetPercentUsed: 25,
            lastHeartbeatAtEpochMillis: heartbeatSecondsAgo.map { KotlinLong(value: millis($0)) }, actions: [],
            currentRun: current)
    }

    @Test func elapsedTimeReadsLikeTheMock() {
        #expect(RunTime.elapsed(seconds: 252) == "4m 12s")
        #expect(RunTime.elapsed(seconds: 45) == "45s")
        #expect(RunTime.elapsed(seconds: 3725) == "1h 02m")
        #expect(RunTime.elapsed(seconds: -5) == "0s", "a clock behind the server never shows a negative time")
    }

    @Test func eventOffsetsAreMinutesAndSecondsFromTheStart() {
        #expect(RunTime.offset(seconds: 2) == "0:02")
        #expect(RunTime.offset(seconds: 175) == "2:55")
        #expect(RunTime.offset(seconds: 238) == "3:58")
        #expect(RunTime.offset(seconds: 3723) == "1:02:03")
        #expect(RunTime.offset(seconds: -3) == "0:00")
    }

    @Test func aLiveRunMeasuresFromItsStartAndAFinishedRunStopsAtItsEnd() {
        let live = RunRowPresentation(row: run(), referenceDate: reference)
        #expect(live.elapsed == "4m 12s")
        #expect(live.compact == "4m")
        #expect(live.isLive)
        let done = RunRowPresentation(
            row: run(status: .succeeded, finishedSecondsAgo: 100), referenceDate: reference)
        #expect(done.elapsed == "2m 32s", "152 seconds between start and finish, not until now")
        #expect(done.isLive == false)
    }

    @Test func aRunRowNamesTheAgentAndTheIssueOrFallsBackToTheRawIdWhenItCouldNotBeRead() {
        #expect(RunRowPresentation(row: run(), referenceDate: reference).title == "Engineer 1 \u{00B7} PAP-1204")
        let unnamed = RunRowPresentation(row: run(identifier: nil, title: nil), referenceDate: reference)
        #expect(unnamed.title == "Engineer 1", "an unreadable issue is left out, never guessed from the id")
    }

    @Test func aRunWithoutAStartTimeHasNoElapsedTime() {
        let queued = RunRowPresentation(row: run(status: .queued, startedSecondsAgo: nil), referenceDate: reference)
        #expect(queued.elapsed == nil)
        #expect(queued.compact == nil)
    }

    @Test func runStatusTonesFollowTheMeaning() {
        #expect(RunRowPresentation.tone(.running) == .success)
        #expect(RunRowPresentation.tone(.succeeded) == .success)
        #expect(RunRowPresentation.tone(.failed) == .critical)
        #expect(RunRowPresentation.tone(.timedOut) == .critical)
        #expect(RunRowPresentation.tone(.cancelled) == .warn)
        #expect(RunRowPresentation.tone(.queued) == .neutral)
    }

    @Test func aRunningAgentsLineNamesItsIssueAndHowLongItHasRun() {
        let line = AgentRowPresentation(
            row: agent(current: run(startedSecondsAgo: 60, identifier: "PAP-1182")), referenceDate: reference)
        #expect(line.statusLine == "Running \u{00B7} PAP-1182 \u{00B7} 1m")
        #expect(line.tone == .success)
    }

    @Test func aPausedAgentDoesNotClaimToBeRunningItsLastRun() {
        let line = AgentRowPresentation(
            row: agent(status: .paused, current: run(startedSecondsAgo: 60, identifier: "PAP-1182")),
            referenceDate: reference)
        #expect(line.statusLine == "Paused \u{00B7} last heartbeat 2m ago")
    }

    @Test func aBudgetPausedAgentSaysWhy() {
        let line = AgentRowPresentation(
            row: agent(name: "Engineer 2", status: .paused, budgetPaused: true), referenceDate: reference)
        #expect(line.statusLine == "Paused by budget hard stop")
        #expect(line.tone == .critical)
        #expect(line.isBudgetPaused)
    }

    @Test func otherAgentLinesStatusThenLifecycleThenLastHeartbeat() {
        let error = AgentRowPresentation(row: agent(name: "QA agent", status: .error), referenceDate: reference)
        #expect(error.statusLine == "Error \u{00B7} last heartbeat 2m ago")
        #expect(error.tone == .critical)
        let resuming = AgentRowPresentation(
            row: agent(status: .paused, lifecycle: "resuming", heartbeatSecondsAgo: nil), referenceDate: reference)
        #expect(resuming.statusLine == "Paused \u{00B7} resuming")
        let idle = AgentRowPresentation(row: agent(name: "CEO", status: .idle), referenceDate: reference)
        #expect(idle.statusLine == "Idle \u{00B7} last heartbeat 2m ago")
        #expect(idle.tone == .neutral)
    }

    @Test func initialsForTheAvatar() {
        #expect(AgentRowPresentation.initials("QA agent") == "QA")
        #expect(AgentRowPresentation.initials("Engineer 2") == "E2")
        #expect(AgentRowPresentation.initials("CTO") == "CTO")
        #expect(AgentRowPresentation.initials("Designer") == "DE")
        #expect(AgentRowPresentation.initials("") == "?")
    }

    @Test func theSpendGaugeFractionIsClampedForTheBar() {
        #expect(SpendGauge.fraction(percent: 62) == 0.62)
        #expect(SpendGauge.fraction(percent: 140) == 1)
        #expect(SpendGauge.fraction(percent: -3) == 0)
    }
}
