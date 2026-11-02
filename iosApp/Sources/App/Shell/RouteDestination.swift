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
