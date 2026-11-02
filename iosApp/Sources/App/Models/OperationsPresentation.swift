import Foundation
import PaperclipKit

/// Durations as the mocks write them. Pure formatting: a time that is negative (a phone clock behind the server's)
/// reads as zero rather than minus.
enum RunTime {
    /// "45s", "4m 12s", "1h 02m": how long a run has been going, or went.
    static func elapsed(seconds: Double) -> String {
        let total = Int(max(0, seconds))
        switch total {
        case ..<60: return "\(total)s"
        case ..<3600: return "\(total / 60)m \(pad(total % 60))s"
        default: return "\(total / 3600)h \(pad(total % 3600 / 60))m"
        }
    }

    /// "0:02", "2:55", "1:02:03": where in the run an event happened.
    static func offset(seconds: Double) -> String {
        let total = Int(max(0, seconds))
        let (hours, minutes, rest) = (total / 3600, total % 3600 / 60, total % 60)
        return hours > 0 ? "\(hours):\(pad(minutes)):\(pad(rest))" : "\(minutes):\(pad(rest))"
    }

    private static func pad(_ value: Int) -> String { value < 10 ? "0\(value)" : "\(value)" }
}

/// A run in a list or at the top of the Run viewer: its words, how long it has run, and its colour.
@MainActor
struct RunRowPresentation: Equatable {
    let title: String
    let issueLine: String?
    let elapsed: String?
    let compact: String?
    let isLive: Bool

    init(row: RunRowUi, referenceDate: Date?) {
        let issue = row.issueIdentifier
        title = [row.agentName, issue].compactMap { $0 }.joined(separator: " \u{00B7} ")
        issueLine = [issue, row.issueTitle].compactMap { $0 }.joined(separator: " \u{00B7} ").nilIfEmpty
        isLive = Self.isLive(row.status)
        if let started = row.startedAtEpochMillis?.int64Value {
            let finished = row.finishedAtEpochMillis.map { Double($0.int64Value) / 1000 }
            let end = finished ?? (referenceDate ?? Date()).timeIntervalSince1970
            let seconds = end - Double(started) / 1000
            elapsed = RunTime.elapsed(seconds: seconds)
            compact = RelativeAge.text(seconds: seconds)
        } else {
            (elapsed, compact) = (nil, nil)
        }
    }

    static func isLive(_ status: RunStatusUi) -> Bool {
        status == .running || status == .queued || status == .scheduledRetry
    }

    static func tone(_ status: RunStatusUi) -> StatusTone {
        switch status {
        case .running, .succeeded: .success
        case .failed, .timedOut: .critical
        case .cancelled, .interrupted: .warn
        case .queued, .scheduledRetry, .unknown: .neutral
        }
    }
}

/// One row of the Agents tab: the line under the name, the colour of its status, the avatar letters.
@MainActor
struct AgentRowPresentation: Equatable {
    let statusLine: String
    let tone: StatusTone
    let isBudgetPaused: Bool

    init(row: AgentRowUi, referenceDate: Date?) {
        isBudgetPaused = row.isBudgetPaused
        let word = row.isBudgetPaused ? AgentsLabel.pausedByBudget.text : L10n.text(row.status)
        var parts = [word]
        if row.isBudgetPaused {
            tone = .critical
        } else {
            tone = Self.tone(row.status)
            if row.status == .running, let run = row.currentRun, RunRowPresentation.isLive(run.status) {
                let presentation = RunRowPresentation(row: run, referenceDate: referenceDate)
                parts += [run.issueIdentifier, presentation.compact].compactMap { $0 }
            } else if let heartbeat = row.lastHeartbeatAtEpochMillis?.int64Value, row.lifecycleState == nil {
                let seconds = (referenceDate ?? Date()).timeIntervalSince1970 - Double(heartbeat) / 1000
                parts.append(AgentsLabel.lastHeartbeat.text(RelativeAge.text(seconds: seconds)))
            }
            if let lifecycle = row.lifecycleState { parts.append(lifecycle) }
        }
        statusLine = parts.joined(separator: " \u{00B7} ")
    }

    static func tone(_ status: AgentStatusUi) -> StatusTone {
        switch status {
        case .running: .success
        case .error: .critical
        case .paused, .pendingApproval: .warn
        case .active, .idle, .terminated, .unknown: .neutral
        }
    }

    /// Two or three letters for the avatar: a short all-capitals name as it is ("CTO", "QA agent" gives "QA"), a
    /// name ending in a number as its first letter and the number ("Engineer 2" gives "E2"), otherwise two letters.
    static func initials(_ name: String) -> String {
        let words = name.split(separator: " ").map(String.init)
        guard let first = words.first else { return "?" }
        if first.count <= 3, first == first.uppercased() { return first }
        if words.count > 1, let last = words.last, last.allSatisfy(\.isNumber) {
            return String(first.prefix(1)).uppercased() + last
        }
        return String(first.prefix(2)).uppercased()
    }
}

/// The month-spend bar: how much of it to fill. The colour never changes with the number; the figures say the rest.
enum SpendGauge {
    static func fraction(percent: Int) -> Double {
        min(1, max(0, Double(percent) / 100))
    }
}

extension String {
    fileprivate var nilIfEmpty: String? { isEmpty ? nil : self }
}
