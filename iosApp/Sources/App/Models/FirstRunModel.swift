import PaperclipKit

/// Screen 15's adapter: the generic holder model plus one method per intent the screen sends.
typealias FirstRunModel = HolderModel<FirstRunStateHolder>

extension HolderModel where Holder == FirstRunStateHolder {
    func addressChanged(_ text: String) { send(FirstRunIntentAddressChanged(text: text)) }
    func nameChanged(_ text: String) { send(FirstRunIntentNameChanged(text: text)) }
    func checkAddress() { send(FirstRunIntentProbeRequested.shared) }
    func continueToPairing() { send(FirstRunIntentContinueRequested.shared) }
    func pair() { send(FirstRunIntentPairRequested.shared) }
    func cancelPairing() { send(FirstRunIntentPairCancelled.shared) }
    func back() { send(FirstRunIntentBackRequested.shared) }
    func chooseCompany(_ id: String) { send(FirstRunIntentDefaultCompanyChosen(companyId: id)) }
    func setAppLock(_ enabled: Bool) { send(FirstRunIntentAppLockChosen(enabled: enabled)) }
    func finish() { send(FirstRunIntentFinishRequested.shared) }
    func dismissError() { send(FirstRunIntentErrorDismissed.shared) }
}
