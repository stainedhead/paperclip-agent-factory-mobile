import PaperclipKit
import SwiftUI

/// Chooses what the app shows: loading, the lock, first run when there is no factory yet (or while the flow that adds
/// one is still on its pairing and secure steps), or the tab shell. It also
/// hides everything in the app switcher.
struct RootView: View {
    let environment: AppEnvironment

    var body: some View {
        ZStack {
            content
                .blur(radius: environment.isObscured ? Self.switcherBlur : 0)
            if environment.isObscured {
                PrivacyShield()
            }
            #if DEBUG
                if environment.configuration.showsDiagnostics {
                    DiagnosticsOverlay(environment: environment)
                }
            #endif
        }
        .background(Palette.background.color.ignoresSafeArea())
        .environment(\.referenceDate, environment.referenceDate)
        .tint(Palette.accent.color)
    }

    private static let switcherBlur: CGFloat = 30

    @ViewBuilder private var content: some View {
        let state = environment.app.state
        if let notice = environment.startupNotice {
            StartupNoticeView(notice: notice)
        } else if !state.isLoaded {
            LoadingState()
        } else if state.lock == .locked {
            LockView(environment: environment)
        } else if !state.hasFactories || environment.firstRunInProgress {
            FirstRunView(model: environment.firstRun, onTryDemo: environment.useDemo)
        } else {
            TabShellView(environment: environment)
        }
    }
}

/// What the shell says instead of running when this build cannot reach real factories (the words are the notice's).
struct StartupNoticeView: View {
    let notice: StartupNotice

    var body: some View {
        EmptyState(
            symbol: "exclamationmark.shield", title: ShellLabel.startupTitle.text, message: L10n.text(notice)
        )
        .accessibilityIdentifier("startup-notice")
    }
}

/// Covers the content while the app is not active so the app-switcher snapshot shows nothing readable.
struct PrivacyShield: View {
    var body: some View {
        Rectangle()
            .fill(.ultraThinMaterial)
            .ignoresSafeArea()
            .overlay { Image(systemName: "paperclip").font(.system(size: 56)).foregroundStyle(.secondary) }
            .accessibilityHidden(true)
            .accessibilityIdentifier("privacy-shield")
    }
}

#if DEBUG
    /// Launch-argument only (`-demo-diagnostics`): a small readout UI tests use to see how often the phone asked for
    /// Face ID. It is compiled into Debug builds only, so a shipped binary has no such overlay and no English in it.
    struct DiagnosticsOverlay: View {
        let environment: AppEnvironment

        /// Seconds since the overlay was created, then the lock, the load and the Inbox as the shell sees them: a
        /// failing UI test's hierarchy shows how far a slow cold start had got.
        private static let started = ProcessInfo.processInfo.systemUptime

        private var trace: String {
            let app = environment.app.state
            let inbox = environment.inbox?.state
            let seconds = Int(ProcessInfo.processInfo.systemUptime - Self.started)
            return "t\(seconds)s lock=\(app.lock) loaded=\(app.isLoaded) factories=\(app.hasFactories) "
                + "inbox=\(inbox.map { "\($0.phase) badge=\($0.badgeCount) total=\($0.totalCount)" } ?? "none")"
        }

        var body: some View {
            TimelineView(.periodic(from: .now, by: 0.25)) { _ in
                VStack {
                    Text("prompts \(environment.biometricPromptCount)")
                        .font(Typography.micro)
                        .padding(Spacing.extraSmall)
                        .background(.thinMaterial)
                        .accessibilityIdentifier("diagnostics-biometric-prompts")
                        .accessibilityLabel("Biometric prompts")
                        .accessibilityValue("\(environment.biometricPromptCount)")
                    Text(trace)
                        .font(Typography.micro)
                        .padding(Spacing.extraSmall)
                        .background(.thinMaterial)
                        .accessibilityIdentifier("diagnostics-trace")
                        .accessibilityLabel(trace)
                    Spacer()
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .allowsHitTesting(false)
        }
    }
#endif
