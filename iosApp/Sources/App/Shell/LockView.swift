import PaperclipKit
import SwiftUI

/// The app lock (PRD FR-014). It asks for the device owner once when it appears; if that is cancelled or fails it
/// stays, says why, and offers the button. The decision to lock and the unlock itself are shared Kotlin.
struct LockView: View {
    let environment: AppEnvironment

    var body: some View {
        VStack(spacing: Spacing.roomy) {
            Image(systemName: "lock.fill")
                .font(.system(size: 56))
                .foregroundStyle(Palette.accent.color)
                .accessibilityHidden(true)
            Text(ShellLabel.lockTitle.text)
                .font(Typography.title)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(ShellLabel.lockMessage.text)
                .font(Typography.body)
                .foregroundStyle(Palette.textSecondary.color)
                .multilineTextAlignment(.center)
            if let issue = environment.app.state.unlockIssue {
                Text(L10n.text(issue))
                    .font(Typography.body)
                    .foregroundStyle(Palette.destructive.color)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("unlock-issue")
            }
            Button {
                environment.unlock()
            } label: {
                Label(ShellLabel.lockUnlock.text, systemImage: "faceid")
            }
            .buttonStyle(.app(.primary))
            .accessibilityIdentifier("unlock-button")
        }
        .padding(Spacing.page)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("lock-screen")
        .task { environment.unlock() }
    }
}
