import SwiftUI

/// A confirmation for an action that does not ask Face ID (sign out, revoke a key): title, what happens, the two
/// buttons. It carries no lock glyph, because none follows.
struct ConfirmSheet: View {
    let title: String
    let message: String
    let confirmTitle: String
    var cancelTitle = ShellLabel.commonCancel.text
    var isWorking = false
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: Spacing.roomy) {
            Text(title).font(Typography.title).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(message).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
                .multilineTextAlignment(.center)
            Button(confirmTitle, action: onConfirm)
                .buttonStyle(.app(.primary))
                .disabled(isWorking)
                .accessibilityIdentifier("confirm-sheet-confirm")
            Button(cancelTitle, action: onCancel)
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("confirm-sheet-cancel")
        }
        .padding(Spacing.page)
        .presentationDetents([.medium, .large])
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("confirm-sheet")
    }
}
