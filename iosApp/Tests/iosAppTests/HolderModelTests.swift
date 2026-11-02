import PaperclipKit
import Testing

@testable import iosApp

/// UI-002: the generic adapter between a shared state holder and SwiftUI.
@MainActor
struct HolderModelTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    @Test func startsFromTheHoldersCurrentState() {
        let holder = graph.createFirstRunStateHolder()
        let model = HolderModel(holder: holder)
        #expect(model.state === holder.state.value)
        #expect(model.isClosed == false)
    }

    @Test func mirrorsStateChangesMadeByTheSharedCore() async {
        let model = HolderModel(holder: graph.createAppStateHolder())
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.isLoaded })
        #expect(model.state.hasFactories)
        #expect(model.state.selectedInstanceName == "Home factory")
        observing.cancel()
    }

    @Test func anIntentIsVisibleImmediatelyWithoutWaitingForTheObservation() {
        let model = HolderModel(holder: graph.createFirstRunStateHolder())
        // No observe() task is running: a stale read would show the empty address.
        model.send(FirstRunIntentAddressChanged(text: "https://factory.example.com"))
        #expect(model.state.address == "https://factory.example.com")
        #expect(model.state.canContinue)
    }

    @Test func cancellingObservationNeverClosesTheHolderAndItCanStartAgain() async {
        let holder = graph.createFirstRunStateHolder()
        var closed = 0
        let model = HolderModel(holder: holder) { _ in closed += 1 }
        let first = Task { await model.observe() }
        await Task.yield()
        first.cancel()
        await first.value
        #expect(model.isClosed == false)
        #expect(closed == 0)

        // A change made while nobody observed is caught up when observation starts again.
        holder.onIntent(intent: FirstRunIntentAddressChanged(text: "https://later.example.com"))
        let second = Task { await model.observe() }
        #expect(await eventually { model.state.address == "https://later.example.com" })
        second.cancel()
        #expect(closed == 0)
    }

    @Test func theOwnerClosesTheHolderExactlyOnce() {
        var closed = 0
        let model = HolderModel(holder: graph.createFirstRunStateHolder()) { holder in
            closed += 1
            holder.close()
        }
        model.close()
        model.close()
        #expect(closed == 1)
        #expect(model.isClosed)
    }

    @Test func aClosedModelIgnoresIntentsAndReturnsFromObserve() async {
        let model = HolderModel(holder: graph.createFirstRunStateHolder())
        model.close()
        model.send(FirstRunIntentAddressChanged(text: "https://ignored.example.com"))
        #expect(model.state.address.isEmpty)
        await model.observe()  // returns at once instead of waiting on a closed holder
    }

    @Test func tellsTheOwnerAboutEveryStateItAppliesIncludingAfterAnIntent() async {
        let model = HolderModel(holder: graph.createFirstRunStateHolder())
        var addresses: [String] = []
        model.didUpdate = { addresses.append($0.address) }
        model.send(FirstRunIntentAddressChanged(text: "https://one.example.com"))
        #expect(addresses == ["https://one.example.com"])
    }
}

/// The explicit owner path for screen models: created once per key, closed when the key leaves, never by a view.
@MainActor
struct ModelStoreTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    @Test func theSameKeyReturnsTheSameModelAndCreatesItOnce() {
        let store = ModelStore<String>()
        var made = 0
        let first = store.model(for: "a") { () -> FirstRunStateHolder in
            made += 1
            return graph.createFirstRunStateHolder()
        }
        let second = store.model(for: "a") { () -> FirstRunStateHolder in
            made += 1
            return graph.createFirstRunStateHolder()
        }
        #expect(first === second)
        #expect(made == 1)
    }

    @Test func retainClosesTheModelsWhoseKeysLeftExactlyOnce() {
        let store = ModelStore<String>()
        let kept = store.model(for: "kept") { graph.createFirstRunStateHolder() }
        let gone = store.model(for: "gone") { graph.createFirstRunStateHolder() }
        store.retain(only: ["kept"])
        store.retain(only: ["kept"])
        #expect(kept.isClosed == false)
        #expect(gone.isClosed)
        #expect(store.keys == ["kept"])
    }

    @Test func closeAllClosesEveryModelAndEmptiesTheStore() {
        let store = ModelStore<Int>()
        let one = store.model(for: 1) { graph.createFirstRunStateHolder() }
        let two = store.model(for: 2) { graph.createFirstRunStateHolder() }
        store.closeAll()
        #expect(one.isClosed && two.isClosed)
        #expect(store.keys.isEmpty)
    }

    @Test func typingWhileObservingNeverShowsAnOlderStateAfterANewerOne() async {
        let holder = graph.createEditInstanceStateHolder(instanceId: "demo-home")
        let model = HolderModel(holder: holder)
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.name == "Home factory" })
        var applied: [String] = []
        model.didUpdate = { applied.append($0.name) }
        let typed = (1...14).map { String("Studio factory".prefix($0)) }
        for text in typed {
            model.send(EditInstanceIntentNameChanged(text: text))
            try? await Task.sleep(for: .milliseconds(3))
        }
        #expect(await eventually { model.state.name == "Studio factory" })
        observing.cancel()

        var longest = 0
        for name in applied where typed.contains(name) {
            #expect(name.count >= longest, "the field went back from \(longest) to \(name.count) characters")
            longest = max(longest, name.count)
        }
    }
}
