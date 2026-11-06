import PaperclipKit
import SwiftUI

/// Starts the shared refresh policy for a detail screen while it is on screen and stops it when it leaves. The
/// interval (15 s while the item can still change, none once it is final) is the state's, never a Swift timer.
struct RefreshWhileVisible: ViewModifier {
    let graph: PaperclipGraph
    let target: any RefreshTarget
    @State private var session: RefreshSession?

    func body(content: Content) -> some View {
        content
            .onAppear { session = graph.refresh.start(target: target) }
            .onDisappear {
                session?.stop()
                session = nil
            }
    }
}

extension View {
    func refreshWhileVisible(graph: PaperclipGraph, target: any RefreshTarget) -> some View {
        modifier(RefreshWhileVisible(graph: graph, target: target))
    }
}

/// What a detail screen shows when it has no content: the loading spinner, the error with its way forward, or the
/// note that it cannot be read while offline.
struct DetailUnavailableView: View {
    let phase: DetailPhase
    let error: ErrorUi?
    let isOffline: Bool
    let onRetry: () -> Void
    let onSignInAgain: () -> Void

    var body: some View {
        switch phase {
        case .loading:
            LoadingState().frame(minHeight: 240)
        case .failed:
            VStack(spacing: Spacing.roomy) {
                if isOffline {
                    EmptyState(
                        symbol: "wifi.slash", title: DetailLabel.offlineTitle.text,
                        message: DetailLabel.offlineMessage.text)
                } else if let error {
                    ErrorBanner(error: error, onRetry: onRetry, onSignInAgain: onSignInAgain)
                } else {
                    EmptyState(symbol: "exclamationmark.triangle", title: DetailLabel.failedTitle.text)
                    Button(ShellLabel.commonRetry.text, action: onRetry)
                        .buttonStyle(.app(.secondary))
                        .accessibilityIdentifier("detail-retry")
                }
            }
        case .content:
            EmptyView()
        }
    }
}

/// The result of one effect or one action, as an icon, a title and a status word. The icon never carries the meaning
/// alone: the status is spelled out.
struct OutcomeRow: View {
    enum Tone {
        case success
        case warn
        case critical
        case neutral

        var symbol: String {
            switch self {
            case .success: "checkmark.circle.fill"
            case .warn: "minus.circle.fill"
            case .critical: "xmark.octagon.fill"
            case .neutral: "clock"
            }
        }

        var color: Palette.Token {
            switch self {
            case .success: Palette.success
            case .warn: Palette.warn
            case .critical: Palette.destructive
            case .neutral: Palette.textSecondary
            }
        }
    }

    let title: String
    let status: String
    var detail: String?
    let tone: Tone
    let identifier: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.large) {
            Image(systemName: tone.symbol)
                .foregroundStyle(tone.color.color)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.hairline) {
                Text(title).font(Typography.body).foregroundStyle(Palette.textPrimary.color)
                Text(status).font(Typography.secondary.weight(.semibold)).foregroundStyle(tone.color.color)
                if let detail {
                    Text(detail).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([title, status, detail].compactMap { $0 }.joined(separator: ". "))
        .accessibilityIdentifier(identifier)
    }
}
