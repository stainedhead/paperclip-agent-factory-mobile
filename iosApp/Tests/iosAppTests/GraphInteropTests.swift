import PaperclipKit
import Testing

/// Checks that the shared state holders added in slice 1a-1 are reachable and usable from Swift.
@MainActor
struct GraphInteropTests {
    private let graph = PaperclipCore.shared.createGraph(allowInsecureLocal: false)

    @Test func appStateStartsLockedAndUnloaded() {
        let holder = graph.createAppStateHolder()
        #expect(holder.state.value.isLoaded == false)
        #expect(holder.state.value.lock == .locked)
    }

    @Test func settingsAlwaysKeepDestructiveAuthOn() {
        let holder = graph.createSettingsStateHolder()
        #expect(holder.state.value.destructiveActionAuthAlwaysOn)
    }

    @Test func addModeStartsWithSaveDisabled() {
        let holder = graph.createEditInstanceStateHolder(instanceId: nil)
        #expect(holder.state.value.mode == .add)
        #expect(holder.state.value.canSave == false)
    }

    @Test func firstRunOffersTheDemoOption() {
        let holder = graph.createFirstRunStateHolder()
        holder.onIntent(intent: FirstRunIntentDemoSelected.shared)
        #expect(holder.state.value.isDemo)
        #expect(holder.state.value.step == .done)
    }

    @Test func factoriesAndManageStartEmpty() {
        #expect(graph.createFactoriesStateHolder().state.value.isEmpty)
        #expect(graph.createManageFactoriesStateHolder().state.value.rows.isEmpty)
    }
}
