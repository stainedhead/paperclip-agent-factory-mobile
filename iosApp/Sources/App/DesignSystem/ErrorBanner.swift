import PaperclipKit
import SwiftUI

/// One localized message per `ErrorKey` (PRD FR-097), with the server's own reason for a rejection and the way
/// forward: sign in again when signed out, otherwise try again.
struct ErrorBanner: View {
    let error: ErrorUi
    var onRetry: (() -> Void)?
    var onSignInAgain: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            Label {
                Text(message)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill").accessibilityHidden(true)
            }
            .font(Typography.body)
            .foregroundStyle(Palette.destructive.color)
            .accessibilityIdentifier("error-message")
            if error.key == .signedOut, let onSignInAgain {
                Button(ShellLabel.commonSignInAgain.text, action: onSignInAgain)
                    .buttonStyle(.app(.primary))
                    .accessibilityIdentifier("error-sign-in-again")
            } else if let onRetry {
                Button(ShellLabel.commonRetry.text, action: onRetry)
                    .buttonStyle(.app(.secondary))
                    .accessibilityIdentifier("error-retry")
            }
        }
        .padding(Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.destructiveSoft.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("error-banner")
    }

    private var message: String {
        let base = L10n.text(error.key)
        guard error.key == .rejected, let detail = error.detail, !detail.isEmpty else { return base }
        return "\(base) \(detail)"
    }
}

/// Shown above cached content while the phone is offline.
struct OfflineBanner: View {
    let updatedAtEpochMillis: Int64?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.extraSmall) {
            Label {
                Text(ShellLabel.commonOffline.text)
            } icon: {
                Image(systemName: "wifi.slash").accessibilityHidden(true)
            }
            .font(Typography.body)
            UpdatedLabel(epochMillis: updatedAtEpochMillis)
        }
        .padding(Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.warnSoft.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .foregroundStyle(Palette.warn.color)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("offline-banner")
    }
}

#Preview("Errors") {
    VStack {
        ErrorBanner(error: ErrorUi(key: .unreachable, detail: nil), onRetry: {})
        ErrorBanner(error: ErrorUi(key: .signedOut, detail: nil), onSignInAgain: {})
        ErrorBanner(error: ErrorUi(key: .rejected, detail: "Budget must be higher."))
        OfflineBanner(updatedAtEpochMillis: 1_000_000)
    }
    .padding()
    .background(Palette.background.color)
}
