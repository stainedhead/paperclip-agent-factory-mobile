import SwiftUI

struct EmptyState: View {
    let symbol: String
    let title: String
    var message: String?
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: Spacing.extraLarge) {
            Image(systemName: symbol)
                .font(.largeTitle)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityHidden(true)
            Text(title).font(Typography.title).multilineTextAlignment(.center)
            if let message {
                Text(message)
                    .font(Typography.body)
                    .foregroundStyle(Palette.textSecondary.color)
                    .multilineTextAlignment(.center)
            }
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.app(.primary))
                    .accessibilityIdentifier("empty-state-action")
            }
        }
        .padding(Spacing.page)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("empty-state")
    }
}

struct LoadingState: View {
    var label = ShellLabel.commonLoading.text

    var body: some View {
        VStack(spacing: Spacing.extraLarge) {
            ProgressView()
            Text(label).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("loading-state")
    }
}

#Preview("Empty") {
    EmptyState(symbol: "tray", title: "All clear", message: "Nothing needs you right now.")
}

#Preview("Loading") {
    LoadingState()
}
