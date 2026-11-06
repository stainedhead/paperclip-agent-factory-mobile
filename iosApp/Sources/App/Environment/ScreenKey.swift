import Foundation

/// Which screen model a route (or a tab root) owns.
enum ScreenKey: Hashable {
    case factories
    case manageFactories
    case editFactory(id: String?)
    case settings
    case decision(id: String)
    case approval(id: String)
    case budget(id: String)
    /// The Factory and Agents tab roots belong to one company of one factory; another company is another model.
    case factoryHome(instance: String, company: String)
    case agents(instance: String, company: String)
    case agent(id: String)
    case run(id: String)
    /// The Work tab root belongs to one company of one factory; the Issue screen to its route.
    case work(instance: String, company: String)
    case issue(id: String)

    init?(_ sheet: AppSheet) {
        switch sheet {
        case .budgetIncident(let id): self = .budget(id: id)
        case .settings: return nil
        }
    }

    init?(_ route: AppRoute) {
        switch route {
        case .manageFactories: self = .manageFactories
        case .editFactory(let id): self = .editFactory(id: id)
        case .settings, .pairedDevices: self = .settings
        case .decision(let id): self = .decision(id: id)
        case .approval(let id): self = .approval(id: id)
        case .agent(let id): self = .agent(id: id)
        case .run(let id): self = .run(id: id)
        case .issue(let id): self = .issue(id: id)
        }
    }
}
