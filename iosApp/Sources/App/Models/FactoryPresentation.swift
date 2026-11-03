import PaperclipKit

enum StatusTone: Equatable {
    case success
    case warn
    case critical
    case neutral
}

/// Which words and colour a factory's status gets, from the two facts the shared state gives (status and key
/// expiry). A lookup, not a rule: whether a key is expired is decided in shared Kotlin.
struct FactoryStatusPresentation: Equatable {
    enum Label: Equatable {
        case connected
        case keyExpired
        case signedOut
        case needsRePairing
    }

    let label: Label
    let tone: StatusTone
    /// "Pair again" is offered whenever this phone has no working key for the factory.
    let offersPairing: Bool

    init(status: FactoryStatusUi, expiry: ExpiryUi) {
        if expiry.state == .expired {
            (label, tone) = (.keyExpired, .warn)
        } else {
            switch status {
            case .signedIn: (label, tone) = (.connected, .success)
            case .signedOut: (label, tone) = (.signedOut, .neutral)
            case .needsRePairing: (label, tone) = (.needsRePairing, .warn)
            }
        }
        offersPairing = status != .signedIn || expiry.state == .expired
    }

    var text: String {
        switch label {
        case .connected: L10n.text(FactoryStatusUi.signedIn)
        case .keyExpired: ShellLabel.statusKeyExpired.text
        case .signedOut: L10n.text(FactoryStatusUi.signedOut)
        case .needsRePairing: L10n.text(FactoryStatusUi.needsRePairing)
        }
    }
}

/// The colour of the dot beside the factory name on the tabs: green only for a working key on a connected phone.
enum SwitcherDot {
    static func tone(status: FactoryStatusUi?, expiry: ExpiryUi?, isOffline: Bool) -> StatusTone {
        guard let status, let expiry else { return .neutral }
        let tone = FactoryStatusPresentation(status: status, expiry: expiry).tone
        return isOffline && tone == .success ? .warn : tone
    }
}

/// "Board key expires in 23 days": the text and colour for a key's expiry, nil when the key never expires.
struct ExpiryPresentation: Equatable {
    let text: String?
    let tone: StatusTone

    init(_ expiry: ExpiryUi) {
        switch expiry.state {
        case .noExpiry:
            (text, tone) = (nil, .neutral)
        case .expired:
            (text, tone) = (ShellLabel.expiryExpired.text, .warn)
        case .valid, .expiringSoon:
            let days = expiry.daysLeft?.intValue ?? 0
            (text, tone) = (ShellLabel.expiryDays.plural(days), expiry.state == .expiringSoon ? .warn : .neutral)
        }
    }
}

/// The words of each confirmation on the Edit factory screen. The "here only" ones appear when the phone could not read
/// the key (Face ID declined, device locked): the factory is signed out or removed on this phone, and the key stays
/// live on the server, which the message says.
@MainActor
enum EditConfirmationPresentation {
    struct Descriptor {
        let title: String
        let message: String
        let confirmTitle: String
    }

    static func descriptor(_ which: EditConfirmation, name: String) -> Descriptor {
        switch which {
        case .signOut:
            Descriptor(
                title: ShellLabel.editSignOutTitle.text(name), message: ShellLabel.editSignOutMessage.text,
                confirmTitle: ShellLabel.editSignOutConfirm.text)
        case .signOutHereOnly:
            Descriptor(
                title: ShellLabel.editSignOutHereOnlyTitle.text(name),
                message: ShellLabel.editSignOutHereOnlyMessage.text,
                confirmTitle: ShellLabel.editSignOutHereOnlyConfirm.text)
        case .remove:
            Descriptor(
                title: ShellLabel.removeTitle.text(name), message: ShellLabel.editRemoveMessage.text,
                confirmTitle: ShellLabel.removeConfirm.text)
        case .removeHereOnly:
            Descriptor(
                title: ShellLabel.removeTitle.text(name), message: ShellLabel.removeHereOnlyMessage.text,
                confirmTitle: ShellLabel.removeHereOnlyConfirm.text)
        }
    }
}
