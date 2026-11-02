import Foundation
import Network
import PaperclipKit
import Testing

/// Records the approval pages the graph asks the system browser to open; the probe tests never pair, so none are.
final class OpenedPages: @unchecked Sendable {
    private let lock = NSLock()
    private var urls: [String] = []

    var isEmpty: Bool { lock.withLock { urls.isEmpty } }

    func record(_ url: String) { lock.withLock { urls.append(url) } }

    /// A pairing browser whose "page" opens and closes at once, as the callback form the shared code offers.
    func makeBrowser() -> PairingBrowser {
        PairingBrowsers.shared.fromCallback { [self] url, finished in
            record(url)
            _ = finished()
        }
    }
}

/// A tiny HTTP server on the loopback interface that answers `GET /api/health` with a fixed JSON body, so the real
/// Darwin HTTP stack (URLSession, ATS) is exercised by the probe. The body can be swapped between requests.
final class LoopbackHealthServer: @unchecked Sendable {
    static let authenticated =
        #"{"status":"ok","deploymentMode":"authenticated","deploymentExposure":"private","#
        + #""localAiLoginSupported":false,"commit":"abc1234","bootstrapStatus":"ready","bootstrapInviteActive":false}"#
    static let localTrusted =
        #"{"status":"ok","version":"2026.1023.0","commit":"abc1234","deploymentMode":"local_trusted","#
        + #""deploymentExposure":"private","authReady":true,"bootstrapStatus":"ready","bootstrapInviteActive":false}"#

    private let queue = DispatchQueue(label: "pafm.loopback")
    private let lock = NSLock()
    private var body = LoopbackHealthServer.authenticated
    private var requests: [String] = []
    private var listener: NWListener?

    var requestLines: [String] { lock.withLock { requests } }

    func serve(_ json: String) { lock.withLock { body = json } }

    /// Starts listening and returns the port the system chose.
    func start() async throws -> UInt16 {
        let listener = try NWListener(using: .tcp)
        self.listener = listener
        listener.newConnectionHandler = { [weak self] connection in self?.handle(connection) }
        return try await withCheckedThrowingContinuation { continuation in
            let once = NSLock()
            nonisolated(unsafe) var resumed = false
            listener.stateUpdateHandler = { state in
                once.lock()
                defer { once.unlock() }
                guard !resumed else { return }
                switch state {
                case .ready:
                    resumed = true
                    continuation.resume(returning: listener.port?.rawValue ?? 0)
                case .failed(let error):
                    resumed = true
                    continuation.resume(throwing: error)
                default:
                    break
                }
            }
            listener.start(queue: queue)
        }
    }

    func stop() { listener?.cancel() }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, _ in
            guard let self, let data, let text = String(data: data, encoding: .utf8) else {
                connection.cancel()
                return
            }
            let requestLine = text.components(separatedBy: "\r\n").first ?? ""
            let json: String = self.lock.withLock {
                self.requests.append(requestLine)
                return self.body
            }
            let found = requestLine.hasPrefix("GET /api/health")
            let payload = found ? json : #"{"error":"Not found"}"#
            let head =
                "HTTP/1.1 \(found ? "200 OK" : "404 Not Found")\r\nContent-Type: application/json\r\n"
                + "Content-Length: \(payload.utf8.count)\r\nConnection: close\r\n\r\n"
            connection.send(
                content: Data((head + payload).utf8),
                completion: .contentProcessed { _ in connection.cancel() })
        }
    }
}

/// Acceptance criterion 6 of Phase 1b: the live graph builds on the simulator and serves every screen's state holder,
/// and a real health probe goes through the real HTTP stack to a loopback server.
@MainActor
struct LiveGraphInteropTests {
    private let pages = OpenedPages()

    /// A graph on a temporary data folder and a Keychain service of its own, so nothing of the app is touched.
    private func makeGraph() -> (graph: PaperclipGraph, folder: URL) {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("pafm-live-\(UUID().uuidString)")
        let settings = IosLiveSettings(
            keychainService: "pafm.live.test.\(UUID().uuidString)",
            dataDirectory: folder.path,
            unlockReason: "Unlock Paperclip",
            confirmReason: "Confirm this action")
        let graph = LiveIosGraph.shared.createLiveGraph(
            allowInsecureLocal: true, pairingBrowser: pages.makeBrowser(), settings: settings)
        return (graph, folder)
    }

    @Test func aFirstLaunchHasNoFactoriesAndNoDemoData() async {
        let (graph, folder) = makeGraph()
        defer { try? FileManager.default.removeItem(at: folder) }
        let settings = graph.createSettingsStateHolder()
        let factories = graph.createFactoriesStateHolder()
        let firstRun = graph.createFirstRunStateHolder()

        // The database answered (the device name arrived) and holds nothing.
        #expect(await eventually { !settings.state.value.deviceName.isEmpty })
        #expect(settings.state.value.factories.isEmpty)
        #expect(factories.state.value.isEmpty)
        #expect(factories.state.value.rows.isEmpty)
        #expect(firstRun.state.value.step == .address)
        #expect(firstRun.state.value.isDemo == false)
        #expect(FileManager.default.fileExists(atPath: folder.appendingPathComponent("paperclip.db").path))

        for close in [settings.close, factories.close, firstRun.close] { close() }
        graph.close()
    }

    @Test func everyStateHolderIsCreatedFromTheLiveGraphAndClosesCleanly() async {
        let (graph, folder) = makeGraph()
        defer { try? FileManager.default.removeItem(at: folder) }
        let missing = "missing-factory"
        let company = "company-none"

        let firstRun = graph.createFirstRunStateHolder()
        let app = graph.createAppStateHolder()
        let factories = graph.createFactoriesStateHolder()
        let manage = graph.createManageFactoriesStateHolder()
        let settings = graph.createSettingsStateHolder()
        let add = graph.createEditInstanceStateHolder(instanceId: nil)
        let edit = graph.createEditInstanceStateHolder(instanceId: missing)
        let inbox = graph.attention.createInboxStateHolder(instanceId: missing, companyId: company)
        let decision = graph.attention.createDecisionStateHolder(
            instanceId: missing, companyId: company, decisionId: "d-1",
            hints: DecisionHints(originAgentName: nil, decideBy: nil, dismissalKey: nil))
        let approval = graph.attention.createApprovalStateHolder(
            instanceId: missing, companyId: company, approvalId: "a-1")
        let budget = graph.attention.createBudgetIncidentStateHolder(
            instanceId: missing, companyId: company, incidentId: "b-1")
        let workList = graph.work.createWorkListStateHolder(instanceId: missing, companyId: company)
        let issue = graph.work.createIssueStateHolder(instanceId: missing, companyId: company, issueId: "i-1")
        let agents = graph.agents.createAgentsStateHolder(instanceId: missing, companyId: company)
        let agent = graph.agents.createAgentDetailStateHolder(
            instanceId: missing, companyId: company, agentId: "g-1")
        let run = graph.runs.createRunViewerStateHolder(instanceId: missing, companyId: company, runId: "r-1")
        let home = graph.factory.createFactoryHomeStateHolder(instanceId: missing, companyId: company)

        // A factory that is not on the device is a typed failure, never a crash and never invented data.
        #expect(await eventually { inbox.state.value.phase != .loading })
        #expect(inbox.state.value.phase != .content)
        #expect(await eventually { decision.state.value.phase != .loading })
        #expect(decision.state.value.phase == .failed)
        #expect(await eventually { agents.state.value.phase != .loading })

        let session = graph.refresh.start(target: inbox.refreshTarget)
        graph.lifecycle.setForeground(isForeground: false)
        graph.lifecycle.setForeground(isForeground: true)
        session.stop()

        let screens: [() -> Void] = [firstRun.close, app.close, factories.close, manage.close, settings.close]
        let edits: [() -> Void] = [add.close, edit.close, inbox.close, decision.close, approval.close, budget.close]
        let details: [() -> Void] = [workList.close, issue.close, agents.close, agent.close, run.close, home.close]
        for close in screens + edits + details { close() }
        graph.close()
    }

    @Test func theFirstRunProbeGoesThroughTheRealHttpStackAndRefusesLocalTrusted() async throws {
        let server = LoopbackHealthServer()
        let port = try await server.start()
        defer { server.stop() }
        let (graph, folder) = makeGraph()
        defer { try? FileManager.default.removeItem(at: folder) }
        let firstRun = graph.createFirstRunStateHolder()
        let address = "http://localhost:\(port)"

        firstRun.onIntent(intent: FirstRunIntentAddressChanged(text: address))
        #expect(firstRun.state.value.canContinue)
        firstRun.onIntent(intent: FirstRunIntentProbeRequested.shared)
        #expect(await eventually { firstRun.state.value.probe == .accepted })
        #expect(firstRun.state.value.exposure == .private)
        #expect(server.requestLines.contains { $0.hasPrefix("GET /api/health") })

        server.serve(LoopbackHealthServer.localTrusted)
        firstRun.onIntent(intent: FirstRunIntentAddressChanged(text: address))
        firstRun.onIntent(intent: FirstRunIntentProbeRequested.shared)
        #expect(await eventually { firstRun.state.value.probe == .refused })
        #expect(firstRun.state.value.probeRefusal == .localTrusted)

        #expect(pages.isEmpty)
        firstRun.close()
        graph.close()
    }

    @Test func aReleaseGraphRefusesAPlainHttpServerWithoutSendingARequest() async throws {
        let server = LoopbackHealthServer()
        let port = try await server.start()
        defer { server.stop() }
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("pafm-live-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: folder) }
        let graph = LiveIosGraph.shared.createLiveGraph(
            allowInsecureLocal: false, pairingBrowser: pages.makeBrowser(),
            settings: IosLiveSettings(
                keychainService: "pafm.live.test.\(UUID().uuidString)", dataDirectory: folder.path,
                unlockReason: "Unlock", confirmReason: "Confirm"))
        let firstRun = graph.createFirstRunStateHolder()

        firstRun.onIntent(intent: FirstRunIntentAddressChanged(text: "http://localhost:\(port)"))

        #expect(firstRun.state.value.canContinue == false)
        #expect(server.requestLines.isEmpty)
        firstRun.close()
        graph.close()
    }
}
