import PaperclipKit

/// Screen 9's adapter (add and edit).
typealias EditModel = HolderModel<EditInstanceStateHolder>

extension HolderModel where Holder == EditInstanceStateHolder {
    func nameChanged(_ text: String) { send(EditInstanceIntentNameChanged(text: text)) }
    func addressChanged(_ text: String) { send(EditInstanceIntentAddressChanged(text: text)) }
    func checkAddress() { send(EditInstanceIntentProbeRequested.shared) }
    func chooseCompany(_ id: String) { send(EditInstanceIntentDefaultCompanyChosen(companyId: id)) }
    func addPin(_ text: String) { send(EditInstanceIntentPinAdded(text: text)) }
    func removePin(_ pin: String) { send(EditInstanceIntentPinRemoved(pin: pin)) }
    func togglePinning(_ enabled: Bool) { send(EditInstanceIntentPinningToggled(enabled: enabled)) }
    func save() { send(EditInstanceIntentSaveRequested.shared) }
    func pairAgain() { send(EditInstanceIntentRePairRequested.shared) }
    func cancelPairing() { send(EditInstanceIntentPairCancelled.shared) }
    func signOut() { send(EditInstanceIntentSignOutRequested.shared) }
    func remove() { send(EditInstanceIntentRemoveRequested.shared) }
    func confirm() { send(EditInstanceIntentConfirmationAccepted.shared) }
    func dismissConfirmation() { send(EditInstanceIntentConfirmationDismissed.shared) }
    func dismissMessage() { send(EditInstanceIntentMessageDismissed.shared) }
}
