import SwiftUI

/// A small status chip. The symbol and the text say what it is; color only reinforces it.
struct StatusPill: View {
    enum Tone {
        case success
        case warn
        case critical
        case neutral

        fileprivate var symbol: String {
            switch self {
            case .success: "checkmark.circle.fill"
            case .warn: "exclamationmark.triangle.fill"
            case .critical: "exclamationmark.octagon.fill"
            case .neutral: "circle.dashed"
            }
        }

        fileprivate var foreground: Palette.Token {
            switch self {
            case .success: Palette.success
            case .warn: Palette.warn
            case .critical: Palette.destructive
            case .neutral: Palette.textSecondary
            }
        }

        fileprivate var background: Palette.Token {
            switch self {
            case .success: Palette.successSoft
            case .warn: Palette.warnSoft
            case .critical: Palette.destructiveSoft
            case .neutral: Palette.muted
            }
        }
    }

    let text: String
    let tone: Tone

    var body: some View {
        Label {
            Text(text)
        } icon: {
            Image(systemName: tone.symbol).accessibilityHidden(true)
        }
        .font(Typography.label)
        .foregroundStyle(tone.foreground.color)
        .padding(.horizontal, Spacing.large)
        .padding(.vertical, Spacing.extraSmall)
        .background(tone.background.color, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("status-pill")
    }
}

#Preview("Status pills") {
    VStack(alignment: .leading) {
        StatusPill(text: "Running", tone: .success)
        StatusPill(text: "Paused", tone: .warn)
        StatusPill(text: "Failed", tone: .critical)
        StatusPill(text: "Idle", tone: .neutral)
    }
    .padding()
}
