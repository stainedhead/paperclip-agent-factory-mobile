import PaperclipKit
import SwiftUI

/// What a company tab shows while the selected factory has no company to show (F-1, ADR-150). The shared app holder's
/// `companyGate` decides which state this is; the view draws it and forwards the re-check (a button, or a pull).
struct CompanyGateView: View {
    let environment: AppEnvironment

    var body: some View {
        let state = environment.app.state
        let gate = state.companyGate
        Group {
            if gate == .checking || gate == .ready {
                LoadingState(label: L10n.text(CompanyGate.checking))
            } else {
                ScrollView {
                    VStack(spacing: Spacing.extraLarge) {
                        Image(systemName: gate == .failed ? "exclamationmark.triangle" : "rectangle.stack")
                            .font(.largeTitle)
                            .foregroundStyle(Palette.textSecondary.color)
                            .accessibilityHidden(true)
                        Text(L10n.text(gate)).font(Typography.title).multilineTextAlignment(.center)
                            .accessibilityAddTraits(.isHeader)
                        Text(L10n.text(CompanyGateMessage(gate)))
                            .font(Typography.body)
                            .foregroundStyle(Palette.textSecondary.color)
                            .multilineTextAlignment(.center)
                        if gate == .failed, let error = state.companyError {
                            ErrorBanner(error: error, onRetry: recheck)
                        }
                        actions(for: gate)
                    }
                    .padding(Spacing.page)
                    .frame(maxWidth: .infinity)
                }
                .refreshable { recheck() }
                .accessibilityIdentifier("company-gate")
            }
        }
        .background(Palette.background.color)
    }

    @ViewBuilder private func actions(for gate: CompanyGate) -> some View {
        if gate == .chooseCompany {
            Button(ShellLabel.companyGateOpenFactories.text) { environment.router.select(.factories) }
                .buttonStyle(.app(.primary))
                .accessibilityIdentifier("company-gate-open-factories")
        }
        if gate != .failed {
            Button(ShellLabel.companyGateRecheck.text, action: recheck)
                .buttonStyle(.app(gate == .chooseCompany ? .secondary : .primary))
                .accessibilityIdentifier("company-gate-recheck")
        }
    }

    private func recheck() { environment.app.send(AppIntentRecheckCompanies.shared) }
}
