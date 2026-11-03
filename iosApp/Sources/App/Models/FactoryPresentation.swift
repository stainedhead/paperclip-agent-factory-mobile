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
            let label = days == 1 ? ShellLabel.expiryDaysOne.text : ShellLabel.expiryDaysOther.text(days)
            (text, tone) = (label, expiry.state == .expiringSoon ? .warn : .neutral)
        }
    }
}
