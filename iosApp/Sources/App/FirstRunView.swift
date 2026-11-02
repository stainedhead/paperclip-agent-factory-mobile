import PaperclipKit
import SwiftUI

/// Screen 15, first step: the factory address. Renders `FirstRunState`; every rule lives in shared Kotlin.
struct FirstRunView: View {
    let model: FirstRunModel

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Factory address", text: addressBinding)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .accessibilityIdentifier("address-field")
                } footer: {
                    footer
                }
                Section {
                    Button("Continue to pairing") {}
                        .disabled(!model.state.canContinue)
                        .accessibilityIdentifier("continue-button")
                }
            }
            .navigationTitle("Add your first factory")
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
            Text(message(for: issue))
                .foregroundStyle(.red)
                .accessibilityIdentifier("address-issue")
        } else if let normalized = model.state.normalizedAddress {
            Text("Will connect to \(normalized)")
                .accessibilityIdentifier("address-normalized")
        }
    }

    private func message(for issue: AddressIssue) -> String {
        switch issue {
        case .missingScheme: String(localized: "Start the address with https://")
        case .mustUseHttps: String(localized: "Use a secure https:// address")
        case .missingHost: String(localized: "Add the server name after https://")
        case .invalidHost: String(localized: "The server name has characters that are not allowed")
        case .invalidPort: String(localized: "The port must be a number from 1 to 65535")
        case .invalidPath: String(localized: "The part of the address after the server name has characters that are not allowed")
        case .removeCredentials: String(localized: "Remove the user name and password from the address")
        case .removeQueryOrFragment: String(localized: "Remove everything after the server address")
        }
    }
}
