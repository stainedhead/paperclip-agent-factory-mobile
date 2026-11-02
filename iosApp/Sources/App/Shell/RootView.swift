import PaperclipKit
import SwiftUI

/// Chooses what the app shows: loading, the lock, first run when there is no factory yet, or the tab shell. It also
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
            if environment.configuration.showsDiagnostics {
                DiagnosticsOverlay(environment: environment)
            }
        }
        .background(Palette.background.color.ignoresSafeArea())
    }

    private static let switcherBlur: CGFloat = 30

    @ViewBuilder private var content: some View {
        let state = environment.app.state
        if !state.isLoaded {
            LoadingState()
        } else if state.lock == .locked {
            LockView(environment: environment)
        } else if !state.hasFactories {
            FirstRunView(model: environment.firstRun, onTryDemo: environment.useDemo)
        } else {
            TabShellView(environment: environment)
        }
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

/// Launch-argument only (`-demo-diagnostics`): a small readout UI tests use to see how often the phone asked for
/// Face ID. It never ships on by default.
struct DiagnosticsOverlay: View {
    let environment: AppEnvironment

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
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .allowsHitTesting(false)
    }
}
