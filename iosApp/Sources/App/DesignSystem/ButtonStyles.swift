import SwiftUI

/// The mocks' four button looks. Every one is at least 44 pt tall and shows the pressed state. A disabled button is
/// muted grey with secondary text (not dimmed), so it stays readable and still passes the contrast audit.
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
            .foregroundStyle((isEnabled ? foreground : Palette.textSecondary).color)
            .background(
                (isEnabled ? background : Palette.muted).color, in: RoundedRectangle(cornerRadius: Radius.button)
            )
            .overlay {
                if isEnabled && (kind == .secondary || kind == .destructive) {
                    RoundedRectangle(cornerRadius: Radius.button).strokeBorder(Palette.border.color)
                }
            }
            .opacity(configuration.isPressed ? 0.75 : 1)
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

/// A selectable card (an option, a choice) that is its own look: it shows the pressed state only. A disabled card is
/// muted by its content (secondary text), never dimmed by the system, which would fail the contrast audit.
struct CardSelectButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.75 : 1)
    }
}

extension ButtonStyle where Self == CardSelectButtonStyle {
    static var cardSelect: CardSelectButtonStyle { CardSelectButtonStyle() }
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
