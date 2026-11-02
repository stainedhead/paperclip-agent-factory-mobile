import PaperclipKit

/// Screen 13's adapter.
typealias SettingsModel = HolderModel<SettingsStateHolder>

extension HolderModel where Holder == SettingsStateHolder {
    func commitDeviceName(_ text: String) { send(SettingsIntentDeviceNameCommitted(text: text)) }
    func toggleAppLock(_ enabled: Bool) { send(SettingsIntentAppLockToggled(enabled: enabled)) }
    func chooseIdle(_ period: IdlePeriod) { send(SettingsIntentIdlePeriodChosen(period: period)) }
    func loadDevices(_ instanceId: String) { send(SettingsIntentPairedDevicesRequested(instanceId: instanceId)) }
    func signOut(_ instanceId: String) { send(SettingsIntentSignOutRequested(instanceId: instanceId)) }
    func signOutEverywhere() { send(SettingsIntentSignOutEverywhereRequested.shared) }
    func requestRevoke(instanceId: String, keyId: String) {
        send(SettingsIntentRevokeKeyRequested(instanceId: instanceId, keyId: keyId))
    }
    func confirm() { send(SettingsIntentConfirmationAccepted.shared) }
    func dismissConfirmation() { send(SettingsIntentConfirmationDismissed.shared) }
    func dismissMessage() { send(SettingsIntentMessageDismissed.shared) }
}
