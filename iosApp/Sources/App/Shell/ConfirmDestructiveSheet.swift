import PaperclipKit
import SwiftUI

/// The confirmation every destructive action goes through (pause an agent, cancel a run, reject, a destructive
/// decision option, a budget raise). It shows the lock glyph so the person knows Face ID follows the tap.
///
/// The prompt itself is not made here. `onConfirm` sends the screen's confirm intent, and the shared use case asks
/// the `Biometrics` port before it calls the server; if the person cancels or fails, no call is made and the state
/// holder reports the `DeviceAuthIssue`, which this sheet shows through `issue`. Keeping the prompt in the use case
/// means there is one gate for every platform and a view can never skip it.
struct ConfirmDestructiveSheet: View {
    let title: String
    let message: String
    let confirmTitle: String
    var issue: DeviceAuthIssue?
    var isWorking = false
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: Spacing.roomy) {
            Image(systemName: "lock.fill")
                .font(.largeTitle)
                .foregroundStyle(Palette.destructive.color)
                .accessibilityHidden(true)
            Text(title).font(Typography.title).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(message).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
                .multilineTextAlignment(.center)
            Label(
                isWorking ? ShellLabel.confirmDestructiveWorking.text : ShellLabel.confirmDestructiveFaceID.text,
                systemImage: "faceid"
            )
            .font(Typography.secondary)
            .foregroundStyle(Palette.textSecondary.color)
            .accessibilityIdentifier("confirm-destructive-faceid")
            if let issue {
                Text(L10n.text(issue))
                    .font(Typography.body)
                    .foregroundStyle(Palette.destructive.color)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("confirm-destructive-issue")
            }
            Button(action: onConfirm) {
                Label(confirmTitle, systemImage: "lock.fill")
            }
            .buttonStyle(.app(.destructive))
            .disabled(isWorking)
            .accessibilityIdentifier("confirm-destructive-confirm")
            Button(ShellLabel.commonCancel.text, action: onCancel)
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("confirm-destructive-cancel")
        }
        .padding(Spacing.page)
        .presentationDetents([.medium, .large])
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("confirm-destructive-sheet")
    }
}

#Preview("Confirm destructive") {
    ConfirmDestructiveSheet(
        title: "Pause Engineer 1?", message: "It stops picking up work until you resume it.",
        confirmTitle: "Pause agent", issue: .cancelled, onConfirm: {}, onCancel: {})
}
