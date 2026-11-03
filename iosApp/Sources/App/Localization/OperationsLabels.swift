import Foundation
import PaperclipKit

extension AgentStatusUi: CatalogKey { static var catalogNamespace: String { "agentStatus" } }
extension RunStatusUi: CatalogKey { static var catalogNamespace: String { "runStatus" } }
extension AgentGroupUi: CatalogKey { static var catalogNamespace: String { "agentGroup" } }
extension AgentActionUi: CatalogKey { static var catalogNamespace: String { "agentAction" } }
extension SpendWindowUi: CatalogKey { static var catalogNamespace: String { "spendWindow" } }

/// Labels of Factory home. Raw values are catalog keys (`factory.<case>`).
enum FactoryLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "factory" }

    case monthSpend
    case utcMonth
    case spendOf
    case spendPercent
    case spendWindowsLabel
    case spendUnavailable
    case tileAgents
    case tileWork
    case tileApprovals
    case tileBudget
    case agentsRunning
    case agentsErrorPaused
    case workOpen
    case workBlockedInProgress
    case approvalsPending
    case incidentsOpen
    case runningNow
    case nothingRunning
    case headerLine
    case emptyTitle
    case emptyMessage
    case openHint

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}

/// Labels of the Agents tab. Raw values are catalog keys (`agents.<case>`).
enum AgentsLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "agents" }

    case count
    case filtersLabel
    case filterAll
    case filterRunning
    case filterAttention
    case pausedByBudget
    case lastHeartbeat
    case viewRun
    case budget
    case pauseTitle
    case pauseMessage
    case pauseConfirm
    case pauseKeep
    case emptyTitle
    case emptyMessage
    case filterEmpty
    case working
    case openHint

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}

/// Labels of Agent detail. Raw values are catalog keys (`agentDetail.<case>`).
enum AgentDetailLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "agentDetail" }

    case role
    case reportsTo
    case currentIssue
    case idleNow
    case currentRun
    case recentRuns
    case noRuns
    case monthSpend
    case budget
    case noBudget
    case budgetUsed
    case setBudget
    case budgetTitle
    case budgetHelp
    case budgetField
    case budgetPlaceholder
    case budgetSave
    case budgetInvalid
    case budgetCancel
    case working

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}

/// Labels of the Run viewer. Raw values are catalog keys (`run.<case>`).
enum RunLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "run" }

    case navTitle
    case heartbeat
    case statusLine
    case events
    case noEvents
    case logTail
    case logEmpty
    case logUnavailable
    case logDropped
    case autoScrollOn
    case autoScrollOff
    case autoScrollHint
    case jumpToLatest
    case cancelRun
    case cancelTitle
    case cancelMessage
    case cancelConfirm
    case cancelKeep
    case cancelling
    case openIssue

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
