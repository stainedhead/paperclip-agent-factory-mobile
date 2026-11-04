import PaperclipKit

/// The shared rule decides which colour family a status has (`ToneUi`); each type below only names the platform colour
/// for it. Nothing here classifies a status.
extension StatusTone {
    init(_ tone: ToneUi) {
        switch tone {
        case .success: self = .success
        case .warn: self = .warn
        case .critical: self = .critical
        case .neutral: self = .neutral
        }
    }
}

extension StatusPill.Tone {
    init(_ tone: ToneUi) {
        switch tone {
        case .success: self = .success
        case .warn: self = .warn
        case .critical: self = .critical
        case .neutral: self = .neutral
        }
    }
}

extension OutcomeRow.Tone {
    init(_ tone: ToneUi) {
        switch tone {
        case .success: self = .success
        case .warn: self = .warn
        case .critical: self = .critical
        case .neutral: self = .neutral
        }
    }
}
