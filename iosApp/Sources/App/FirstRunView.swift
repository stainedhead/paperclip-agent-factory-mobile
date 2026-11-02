import PaperclipKit
import SwiftUI

/// Screen 15, first step: the factory address. Renders `FirstRunState`; every rule lives in shared Kotlin.
struct FirstRunView: View {
    let model: FirstRunModel
    var onTryDemo: (() -> Void)?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(ShellLabel.firstRunAddressPlaceholder.text, text: addressBinding)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .accessibilityIdentifier("address-field")
                } footer: {
                    footer
                }
                Section {
                    Button(ShellLabel.firstRunContinue.text) {}
                        .disabled(!model.state.canContinue)
                        .accessibilityIdentifier("continue-button")
                }
                if let onTryDemo {
                    Section {
                        Button(ShellLabel.firstRunTryDemo.text, action: onTryDemo)
                            .accessibilityIdentifier("try-demo-button")
                    }
                }
            }
            .navigationTitle(ShellLabel.firstRunTitle.text)
        }
        .task { await model.observe() }
    }

    /// The text field shows the shared state and sends every edit as an intent: one source of truth.
    private var addressBinding: Binding<String> {
        Binding(
            get: { model.state.address },
            set: { model.addressChanged($0) })
    }

    @ViewBuilder
    private var footer: some View {
        if let issue = model.state.issue {
            Text(L10n.text(issue))
                .foregroundStyle(Palette.destructive.color)
                .accessibilityIdentifier("address-issue")
        } else if let normalized = model.state.normalizedAddress {
            Text(ShellLabel.firstRunWillConnect.text(normalized))
                .accessibilityIdentifier("address-normalized")
        }
    }
}
