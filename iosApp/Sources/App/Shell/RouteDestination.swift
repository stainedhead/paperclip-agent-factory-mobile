import SwiftUI

/// The screen a route pushes. Routes whose screen is not built yet show the labelled placeholder.
struct RouteDestination: View {
    let route: AppRoute
    let environment: AppEnvironment

    var body: some View {
        switch route {
        case .editFactory(let id): EditFactoryView(environment: environment, instanceId: id)
        case .settings: SettingsView(environment: environment)
        case .pairedDevices: PairedDevicesView(environment: environment)
        case .manageFactories: ManageFactoriesView(environment: environment)
        default: RoutePlaceholderView(route: route)
        }
    }
}

/// The sheet the router presents. The budget incident sheet is built in a later milestone; until then it says so.
struct SheetDestination: View {
    let sheet: AppSheet
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            EmptyState(
                symbol: "hammer", title: ShellLabel.placeholderTitle.text, message: InboxLabel.placeholderBudget.text
            )
            .accessibilityIdentifier("sheet-placeholder-budget")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(ShellLabel.commonDone.text) { dismiss() }
                        .accessibilityIdentifier("sheet-placeholder-done")
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
