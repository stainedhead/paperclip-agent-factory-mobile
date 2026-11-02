import SwiftUI

/// The mocks' four button looks. Every one is at least 44 pt tall and shows the pressed state.
struct AppButtonStyle: ButtonStyle {
    enum Kind {
        case primary
        case secondary
        case destructive
        case muted
    }

    let kind: Kind
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Typography.button)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget)
            .padding(.horizontal, Spacing.medium)
            .foregroundStyle(foreground.color)
            .background(background.color, in: RoundedRectangle(cornerRadius: Radius.button))
            .overlay {
                if kind == .secondary || kind == .destructive {
                    RoundedRectangle(cornerRadius: Radius.button).strokeBorder(Palette.border.color)
                }
            }
            .opacity(isEnabled ? (configuration.isPressed ? 0.75 : 1) : 0.4)
            .contentShape(RoundedRectangle(cornerRadius: Radius.button))
    }

    private var foreground: Palette.Token {
        switch kind {
        case .primary: Palette.onAccent
        case .destructive: Palette.destructive
        case .secondary, .muted: Palette.textPrimary
        }
    }

    private var background: Palette.Token {
        switch kind {
        case .primary: Palette.accent
        case .muted: Palette.muted
        case .secondary, .destructive: Palette.surface
        }
    }
}

extension ButtonStyle where Self == AppButtonStyle {
    static func app(_ kind: AppButtonStyle.Kind) -> AppButtonStyle { AppButtonStyle(kind: kind) }
}

#Preview("Buttons") {
    VStack(spacing: Spacing.medium) {
        Button("Review options") {}.buttonStyle(.app(.primary))
        Button("Reject") {}.buttonStyle(.app(.destructive))
        Button("Keep paused") {}.buttonStyle(.app(.secondary))
        Button("Details") {}.buttonStyle(.app(.muted))
        Button("Disabled") {}.buttonStyle(.app(.primary)).disabled(true)
    }
    .padding()
    .background(Palette.background.color)
}
