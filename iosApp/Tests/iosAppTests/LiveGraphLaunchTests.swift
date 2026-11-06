import Foundation
import PaperclipKit
import Testing

/// F-B-5 and F-B-8: the launch path of the live graph reports a failure as an `Error` to catch, and the pairing
/// browser can be made for the main thread with a dismiss hook (`PairingBrowsers.onMainThread`).
struct LiveGraphLaunchTests {
    private func settings(folder: String) -> IosLiveSettings {
        IosLiveSettings(
            keychainService: "pafm.live.launch.\(UUID().uuidString)",
            dataDirectory: folder,
            unlockReason: "Unlock Paperclip",
            confirmReason: "Confirm this action",
            keyReadReason: "Read the board key")
    }

    @Test func anUnusableDataFolderIsAnErrorToCatchAndNotAnAbort() {
        // A plain file where the data folder should be.
        let blocker = FileManager.default.temporaryDirectory
            .appendingPathComponent("pafm-blocker-\(UUID().uuidString)")
        FileManager.default.createFile(atPath: blocker.path, contents: Data())
        defer { try? FileManager.default.removeItem(at: blocker) }

        #expect(throws: (any Error).self) {
            _ = try LiveIosGraph.shared.openLiveGraph(
                allowInsecureLocal: true,
                pairingBrowser: OpenedPages().makeBrowser(),
                settings: settings(folder: blocker.path))
        }
    }

    @Test func aUsableDataFolderOpensTheGraph() throws {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("pafm-launch-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: folder) }

        // The browser the app should use: shown and dismissed on the main thread (its behaviour is tested in Kotlin).
        let browser = PairingBrowsers.shared.onMainThread(show: { _, finished in _ = finished() }, dismiss: {})
        let graph = try LiveIosGraph.shared.openLiveGraph(
            allowInsecureLocal: true, pairingBrowser: browser, settings: settings(folder: folder.path))
        graph.close()
    }
}
