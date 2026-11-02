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
        case .decision(let id): DecisionView(environment: environment, id: id)
        case .approval(let id): ApprovalView(environment: environment, id: id)
        case .agent(let id): AgentDetailView(environment: environment, id: id)
        case .run(let id): RunViewerView(environment: environment, id: id)
        default: RoutePlaceholderView(route: route)
        }
    }
}

/// The sheet the router presents.
struct SheetDestination: View {
    let sheet: AppSheet
    let environment: AppEnvironment

    var body: some View {
        switch sheet {
        case .budgetIncident(let id): BudgetIncidentSheet(environment: environment, id: id)
        case .settings: SettingsView(environment: environment)
        }
    }
}
