import PaperclipKit
import SwiftUI

/// The one button a banner offers: sign in again when signed out, otherwise try again, otherwise just close it.
enum ErrorBannerButton: Equatable {
    case signIn
    case retry
    case dismiss

    static func choose(signedOut: Bool, signIn: Bool, retry: Bool, dismiss: Bool) -> ErrorBannerButton? {
        if signedOut && signIn { return .signIn }
        if retry { return .retry }
        return dismiss ? .dismiss : nil
    }

    var title: String {
        switch self {
        case .signIn: ShellLabel.commonSignInAgain.text
        case .retry: ShellLabel.commonRetry.text
        case .dismiss: ShellLabel.commonDismiss.text
        }
    }

    var identifier: String {
        switch self {
        case .signIn: "error-sign-in-again"
        case .retry: "error-retry"
        case .dismiss: "error-dismiss"
        }
    }
}

/// One localized message per `ErrorKey` (PRD FR-097), with the server's own reason for a rejection and the way
/// forward: sign in again when signed out, otherwise try again.
struct ErrorBanner: View {
    let error: ErrorUi
    var onRetry: (() -> Void)?
    var onSignInAgain: (() -> Void)?
    /// For a banner that retries nothing: it only closes the message.
    var onDismiss: (() -> Void)?

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
            if let button = ErrorBannerButton.choose(
                signedOut: error.key == .signedOut, signIn: onSignInAgain != nil, retry: onRetry != nil,
                dismiss: onDismiss != nil)
            {
                Button(button.title) { act(button) }
                    .buttonStyle(.app(button == .signIn ? .primary : .secondary))
                    .accessibilityIdentifier(button.identifier)
            }
        }
        .padding(Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.destructiveSoft.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("error-banner")
    }

    private func act(_ button: ErrorBannerButton) {
        switch button {
        case .signIn: onSignInAgain?()
        case .retry: onRetry?()
        case .dismiss: onDismiss?()
        }
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
