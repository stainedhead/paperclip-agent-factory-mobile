import PaperclipKit

/// Screen 8's adapter.
typealias FactoriesModel = HolderModel<FactoriesStateHolder>

extension HolderModel where Holder == FactoriesStateHolder {
    func refresh() { send(FactoriesIntentRefresh.shared) }
    func selectInstance(_ id: String) { send(FactoriesIntentInstanceSelected(instanceId: id)) }
    func selectCompany(instanceId: String, companyId: String) {
        send(FactoriesIntentCompanySelected(instanceId: instanceId, companyId: companyId))
    }
}
