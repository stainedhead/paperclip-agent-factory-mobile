import PaperclipKit

/// Screen 16's adapter.
typealias ManageModel = HolderModel<ManageFactoriesStateHolder>

extension HolderModel where Holder == ManageFactoriesStateHolder {
    func reorder(_ orderedIds: [String]) { send(ManageFactoriesIntentReordered(orderedIds: orderedIds)) }
    func requestRemove(_ id: String) { send(ManageFactoriesIntentRemoveRequested(id: id)) }
    func confirmRemove() { send(ManageFactoriesIntentRemoveConfirmed.shared) }
    func cancelRemove() { send(ManageFactoriesIntentRemoveCancelled.shared) }
    func dismissMessage() { send(ManageFactoriesIntentMessageDismissed.shared) }
}
