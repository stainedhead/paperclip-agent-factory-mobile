import Foundation
import PaperclipKit

/// A key the shared core hands the app (or a label the app owns) that maps to one String Catalog entry:
/// `<namespace>.<caseName>`, for example `error.certificateInvalid`. Add a case, add the catalog entry; the
/// completeness test fails until both exist.
protocol CatalogKey {
    static var catalogNamespace: String { get }
}

extension CatalogKey {
    var catalogKey: String { "\(Self.catalogNamespace).\(self)" }
}

extension ErrorKey: CatalogKey { static var catalogNamespace: String { "error" } }
extension ActionsDisabledReason: CatalogKey { static var catalogNamespace: String { "disabled" } }
extension AddressIssue: CatalogKey { static var catalogNamespace: String { "address" } }
extension DeviceAuthIssue: CatalogKey { static var catalogNamespace: String { "deviceAuth" } }
extension EffectKindUi: CatalogKey { static var catalogNamespace: String { "effect" } }
extension FeedbackKind: CatalogKey { static var catalogNamespace: String { "feedback" } }
extension InvalidKey: CatalogKey { static var catalogNamespace: String { "invalid" } }
extension FactoryStatusUi: CatalogKey { static var catalogNamespace: String { "status" } }
extension ProbeStatus: CatalogKey { static var catalogNamespace: String { "probe" } }
extension ProbeRefusal: CatalogKey { static var catalogNamespace: String { "refusal" } }
extension PairingStatus: CatalogKey { static var catalogNamespace: String { "pairing" } }
extension InstanceNameIssue: CatalogKey { static var catalogNamespace: String { "nameIssue" } }
extension Exposure: CatalogKey { static var catalogNamespace: String { "exposure" } }
extension RoleUi: CatalogKey { static var catalogNamespace: String { "role" } }
extension RemovalEffect: CatalogKey { static var catalogNamespace: String { "removal" } }
extension ServerKeyOutcome: CatalogKey { static var catalogNamespace: String { "keyOutcome" } }
extension IdlePeriod: CatalogKey { static var catalogNamespace: String { "idle" } }
extension PairedDevicesStatus: CatalogKey { static var catalogNamespace: String { "devices" } }

enum L10n {
    /// The localized text for `key`; extra arguments fill `%@` placeholders in order.
    static func text<K: CatalogKey>(_ key: K, _ arguments: CVarArg...) -> String {
        text(key, arguments: arguments)
    }

    static func text<K: CatalogKey>(_ key: K, arguments: [CVarArg]) -> String {
        format(resolve(key.catalogKey), arguments)
    }

    /// The catalog text for a raw key, or the key itself when it is missing so a gap is visible, never blank.
    static func resolve(_ key: String) -> String {
        Bundle.main.localizedString(forKey: key, value: key, table: nil)
    }

    private static func format(_ template: String, _ arguments: [CVarArg]) -> String {
        arguments.isEmpty ? template : String(format: template, arguments: arguments)
    }
}

/// Labels the app itself owns (shell, first run, lock). Raw values are catalog keys.
enum ShellLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "label" }

    case tabInbox
    case tabFactory
    case tabWork
    case tabAgents
    case tabFactories
    case placeholderTitle
    case placeholderMessage
    case lockTitle
    case lockMessage
    case lockUnlock
    case firstRunTitle
    case firstRunAddressPlaceholder
    case firstRunContinue
    case firstRunWillConnect
    case firstRunTryDemo
    case settingsTitle
    case settingsAppLock
    case settingsIdlePeriod
    case commonLoading
    case commonEmptyTitle
    case commonRetry
    case commonCancel
    case commonUpdatedAgo
    case commonOffline
    case commonSignInAgain
    case confirmDestructiveTitle
    case confirmDestructiveFaceID
    case confirmDestructiveWorking
    case severityCritical
    case severityHigh
    case severityMedium
    case severityLow
    case firstRunHeadline
    case firstRunIntro
    case stepAddress
    case stepPair
    case stepSecure
    case stepCurrent
    case fieldAddressLabel
    case fieldAddressHelp
    case fieldNameLabel
    case fieldNamePlaceholder
    case fieldNameHelp
    case checkAddress
    case checking
    case probeFoundTitle
    case probeVersion
    case probeRefusedTitle
    case firstRunKeyNote
    case firstRunContinueToPairing
    case pairTitle
    case pairIntro
    case pairOpen
    case pairWaiting
    case pairCancel
    case pairTryAgain
    case secureTitle
    case secureIntro
    case secureCompanies
    case secureAppLock
    case secureAppLockHelp
    case secureFinish
    case commonBack
    case commonDone
    case commonSave
    case commonRemove
    case commonKeep
    case commonEdit
    case commonDismiss
    case commonOn
    case commonOff
    case factoriesSettings
    case factoriesAdd
    case factoriesPairAgain
    case factoriesEmptyTitle
    case factoriesEmptyMessage
    case factoriesCurrent
    case factoriesLoadingCompanies
    case factoriesCompanySelected
    case expiryDaysOne
    case expiryDaysOther
    case expiryExpired
    case statusKeyExpired
    case manageCountOne
    case manageCountOther
    case manageHint
    case manageRemoveAction
    case manageMoveUp
    case manageMoveDown
    case manageReorderRejected
    case removeTitle
    case removeFooter
    case removeConfirm
    case editTitleEdit
    case editTitleAdd
    case editSectionFactory
    case editSectionCompanies
    case editSectionAccess
    case editSectionAccessAnonymous
    case editOpenOnLaunch
    case editNoDefaultCompany
    case editBoardKey
    case editNoCompaniesLoaded
    case editSectionSecurity
    case editPinTitle
    case editPinHelp
    case editPinPlaceholder
    case editPinAdd
    case editPinInvalid
    case editPairAgain
    case editSignOut
    case editRemove
    case editAddressChangeWarning
    case editSaveNeedsCheck
    case editSignOutTitle
    case editSignOutMessage
    case editSignOutConfirm
    case editSaved
    case editOfflineNote
    case settingsThisPhone
    case settingsDeviceName
    case settingsDeviceNameHelp
    case settingsDeviceNameInvalid
    case settingsDestructive
    case settingsDestructiveHelp
    case settingsEachFactory
    case settingsKeysAndDevices
    case settingsPairedDevices
    case settingsAccountHelp
    case settingsSignOutEverywhere
    case settingsSignOutEverywhereTitle
    case settingsSignOutEverywhereMessage
    case settingsSignOutEverywhereConfirm
    case settingsUnconfirmedRevokes
    case settingsSignOutFailures
    case settingsDeviceNameSave
    case devicesTitle
    case devicesChooseFactory
    case devicesThisPhone
    case devicesLastUsed
    case devicesNeverUsed
    case devicesExpiresNever
    case devicesRevoke
    case devicesLoading
    case devicesFailed
    case devicesEmpty
    case revokeTitle
    case revokeMessage
    case revokeThisPhoneMessage
    case revokeConfirm
    case settingsSignOutOne
    case settingsSignedInAs
}

extension ShellLabel {
    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
