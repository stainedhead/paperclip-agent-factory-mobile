import PaperclipKit

/// The models of Factory home, Agents, Agent detail and the Run viewer, owned like every other screen model: the tab
/// roots by company (closed when the company changes), the pushed screens by route (closed when it leaves the stack).
extension AppEnvironment {
    func factoryHomeModel() -> FactoryHomeModel {
        let model = models.model(for: .factoryHome(instance: selectedInstance, company: selectedCompany)) {
            graph.factory.createFactoryHomeStateHolder(instanceId: selectedInstance, companyId: selectedCompany)
        }
        watchForOfflineAfterDetail(model)
        return model
    }

    func agentsModel() -> AgentsModel {
        let model = models.model(for: .agents(instance: selectedInstance, company: selectedCompany)) {
            graph.agents.createAgentsStateHolder(instanceId: selectedInstance, companyId: selectedCompany)
        }
        watchForOfflineAfterDetail(model)
        return model
    }

    func agentDetailModel(id: String) -> AgentDetailModel {
        let model = models.model(for: .agent(id: id)) {
            graph.agents.createAgentDetailStateHolder(
                instanceId: selectedInstance, companyId: selectedCompany, agentId: id)
        }
        watchForOfflineAfterDetail(model)
        return model
    }

    func runModel(id: String) -> RunViewerModel {
        let model = models.model(for: .run(id: id)) {
            graph.runs.createRunViewerStateHolder(instanceId: selectedInstance, companyId: selectedCompany, runId: id)
        }
        watchForOfflineAfterDetail(model)
        return model
    }

    func workModel() -> WorkListModel {
        let model = models.model(for: .work(instance: selectedInstance, company: selectedCompany)) {
            graph.work.createWorkListStateHolder(instanceId: selectedInstance, companyId: selectedCompany)
        }
        watchForOfflineAfterDetail(model)
        return model
    }

    func issueModel(id: String) -> IssueScreenModel {
        let model = models.model(for: .issue(id: id)) {
            graph.work.createIssueStateHolder(instanceId: selectedInstance, companyId: selectedCompany, issueId: id)
        }
        watchForOfflineAfterDetail(model)
        return model
    }

    private func watchForOfflineAfterDetail<Holder: StateHolderProtocol>(_ model: HolderModel<Holder>) {
        guard configuration.offlineAfterDetail else { return }
        model.didUpdate = { [weak self] state in
            if let phase = Self.phase(of: state) { self?.detailLoaded(phase) }
        }
    }

    private static func phase(of state: AnyObject) -> DetailPhase? {
        switch state {
        case let state as FactoryHomeState: state.phase
        case let state as AgentsState: state.phase
        case let state as AgentDetailState: state.phase
        case let state as RunViewerState: state.phase
        case let state as WorkListState: state.phase
        case let state as IssueState: state.phase
        default: nil
        }
    }
}
