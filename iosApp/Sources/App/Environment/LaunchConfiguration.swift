import PaperclipKit

/// What the process was launched with. Everything here is for demo mode and UI tests; none of it changes how a real
/// factory behaves.
///
/// - `-demo`: run on the demo graph (the design mocks' data) instead of the live seam.
/// - `-demo-first-run`: run on fakes that start with no factory and approve every pairing (the first-run flow).
/// - `-demo-biometrics cancelled,failed,approved`: script the device-owner prompt for the demo graph, in order.
/// - `-demo-idle immediately`: set the app lock's idle period at launch (so one background trip re-locks).
/// - `-demo-tab factories`: start on that tab. `-demo-open settings|manage|add|edit:<id>`: push that screen on it.
/// - `-demo-offline`: take the demo phone offline once the Inbox has loaded, so the offline states can be seen.
/// - `-demo-resolved-elsewhere decision,approval,budget`: those items were already resolved on the web, so their
///   screens open on the final state (their Inbox cards may still be listed, as in a feed read a moment earlier).
/// - `-demo-offline-after-detail`: go offline once the first Decision, Approval or Budget screen has its content.
/// - `-demo-open decision:<id>|approval:<id>|budget:<id>`: also open one of those screens.
/// - `-demo-diagnostics`: show a small read-only counter overlay (prompt count) that UI tests can read.
struct LaunchConfiguration {
    let useDemo: Bool
    let useFirstRunDemo: Bool
    let scriptedBiometrics: [BiometricResult]
    let demoIdlePeriod: IdlePeriod?
    let showsDiagnostics: Bool
    let goesOffline: Bool
    let offlineAfterDetail: Bool
    let resolvedElsewhere: Set<String>
    let startTab: AppTab?
    let startRoute: String?

    init(arguments: [String]) {
        useDemo = arguments.contains("-demo")
        useFirstRunDemo = arguments.contains("-demo-first-run")
        showsDiagnostics = arguments.contains("-demo-diagnostics")
        goesOffline = arguments.contains("-demo-offline")
        offlineAfterDetail = arguments.contains("-demo-offline-after-detail")
        resolvedElsewhere = Set(
            (Self.value(after: "-demo-resolved-elsewhere", in: arguments) ?? "").split(separator: ",").map(String.init))
        startTab = Self.value(after: "-demo-tab", in: arguments).flatMap { AppTab(rawValue: $0) }
        startRoute = Self.value(after: "-demo-open", in: arguments)
        scriptedBiometrics =
            (Self.value(after: "-demo-biometrics", in: arguments) ?? "")
            .split(separator: ",")
            .compactMap { Self.biometricResult(String($0)) }
        demoIdlePeriod = Self.value(after: "-demo-idle", in: arguments).flatMap(Self.idlePeriod)
    }

    static var current: LaunchConfiguration {
        LaunchConfiguration(arguments: CommandLine.arguments)
    }

    private static func value(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), arguments.indices.contains(index + 1) else { return nil }
        return arguments[index + 1]
    }

    private static func biometricResult(_ word: String) -> BiometricResult? {
        switch word.lowercased() {
        case "approved": .approved
        case "cancelled": .cancelled
        case "failed": .failed
        case "lockedout": .lockedOut
        case "unavailable": .unavailable
        default: nil
        }
    }

    private static func idlePeriod(_ word: String) -> IdlePeriod? {
        switch word.lowercased() {
        case "immediately": .immediately
        case "oneminute": .oneMinute
        case "fiveminutes": .fiveMinutes
        case "fifteenminutes": .fifteenMinutes
        case "thirtyminutes": .thirtyMinutes
        case "onehour": .oneHour
        default: nil
        }
    }
}

/// Where `-demo-open` begins: a pushed route, or a presented sheet. A lookup over launch text, nothing more.
enum StartLocation: Equatable {
    case route(AppRoute)
    case sheet(AppSheet)

    static func parse(_ name: String) -> StartLocation? {
        switch name {
        case "settings": return .route(.settings)
        case "manage": return .route(.manageFactories)
        case "add": return .route(.editFactory(id: nil))
        default: break
        }
        let parts = name.split(separator: ":", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return nil }
        switch parts[0] {
        case "edit": return .route(.editFactory(id: parts[1]))
        case "decision": return .route(.decision(id: parts[1]))
        case "approval": return .route(.approval(id: parts[1]))
        case "budget": return .sheet(.budgetIncident(id: parts[1]))
        default: return nil
        }
    }
}
