import PaperclipKit
import Testing

@testable import iosApp

/// Screen 8 through its model on the demo graph: the mock's three factories, chips after a refresh, selection.
@MainActor
struct FactoriesModelTests {
    private struct Rig {
        let model: FactoriesModel
        let observing: Task<Void, Never>
        let graph: PaperclipGraph
    }

    private func makeRig() -> Rig {
        let graph = PaperclipCore.shared.createDemoGraph()
        let model = FactoriesModel(holder: graph.createFactoriesStateHolder())
        return Rig(model: model, observing: Task { await model.observe() }, graph: graph)
    }

    @Test func listsTheMockFactoriesWithStatusAndExpiry() async {
        let rig = makeRig()
        let (model, observing) = (rig.model, rig.observing)
        #expect(await eventually { model.state.rows.count == 3 })
        let rows = model.state.rows
        #expect(rows.map(\.name) == ["Home factory", "Cloud factory", "Test factory"])
        #expect(FactoryStatusPresentation(status: rows[0].status, expiry: rows[0].expiry).text == "Connected")
        #expect(ExpiryPresentation(rows[0].expiry).text == "Board key expires in 23 days")
        #expect(FactoryStatusPresentation(status: rows[1].status, expiry: rows[1].expiry).text == "Key expired")
        #expect(FactoryStatusPresentation(status: rows[2].status, expiry: rows[2].expiry).text == "Signed out")
        #expect(rows[0].isSelected)
        observing.cancel()
        model.close()
    }

    @Test func refreshLoadsTheCompanyChipsAndTheUpdatedTime() async {
        let rig = makeRig()
        let (model, observing) = (rig.model, rig.observing)
        #expect(await eventually { model.state.rows.count == 3 })
        #expect(model.state.rows[0].companies.isEmpty)
        model.refresh()
        #expect(await eventually { model.state.rows[0].companies.count == 2 })
        #expect(model.state.rows[0].companies.map(\.name) == ["Acme Labs", "Side Project Co"])
        #expect(await eventually { model.state.updatedAtEpochMillis != nil })
        observing.cancel()
        model.close()
    }

    @Test func choosingACompanyAndAFactoryChangesTheSelection() async {
        let rig = makeRig()
        let (model, observing, graph) = (rig.model, rig.observing, rig.graph)
        let app = HolderModel(holder: graph.createAppStateHolder())
        let appObserving = Task { await app.observe() }
        #expect(await eventually { model.state.rows.count == 3 && app.state.isLoaded })

        model.selectCompany(instanceId: "demo-home", companyId: "company-side")
        #expect(await eventually { app.state.selectedCompanyId == "company-side" })
        model.selectInstance("demo-cloud")
        #expect(await eventually { model.state.selectedInstanceId == "demo-cloud" })
        appObserving.cancel()
        observing.cancel()
        app.close()
        model.close()
    }
}

/// 1b's "key could not be read" paths: the sheets that offer to act on this phone only say so, and each confirmation
/// has its own words.
@MainActor
struct EditConfirmationPresentationTests {
    @Test func everyConfirmationHasATitleAMessageAndAConfirmButton() {
        for which in [EditConfirmation.signOut, .remove, .signOutHereOnly, .removeHereOnly] {
            let text = EditConfirmationPresentation.descriptor(which, name: "Home factory")
            #expect(!text.title.isEmpty && !text.message.isEmpty && !text.confirmTitle.isEmpty)
            #expect(text.title.contains("Home factory"))
        }
    }

    @Test func theHereOnlyVersionsSayTheKeyStaysLiveOnTheServer() {
        for which in [EditConfirmation.signOutHereOnly, .removeHereOnly] {
            let text = EditConfirmationPresentation.descriptor(which, name: "Home factory")
            #expect(text.message.localizedCaseInsensitiveContains("server"))
            #expect(text.message != EditConfirmationPresentation.descriptor(.signOut, name: "Home factory").message)
        }
    }
}
