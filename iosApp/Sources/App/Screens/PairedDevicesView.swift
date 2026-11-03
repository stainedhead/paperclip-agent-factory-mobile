import PaperclipKit
import SwiftUI

/// "Paired devices and keys": the board keys of the person's account on one factory, this phone's marked, each with
/// Revoke. It shares the Settings model: the shared holder owns the list and the revoke confirmation.
struct PairedDevicesView: View {
    let environment: AppEnvironment
    private var model: SettingsModel { environment.settingsModel() }
    @State private var selectedFactory = ""

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                if let error = state.error { ErrorBanner(error: error, onDismiss: model.dismissMessage) }
                if state.factories.count > 1 {
                    Picker(ShellLabel.devicesChooseFactory.text, selection: $selectedFactory) {
                        ForEach(state.factories, id: \.id) { Text($0.name).tag($0.id) }
                    }
                    .pickerStyle(.menu)
                    .accessibilityIdentifier("devices-factory-picker")
                }
                if let name = state.profileName {
                    Text(ShellLabel.settingsSignedInAs.text(name)).font(Typography.body)
                        .foregroundStyle(Palette.textSecondary.color)
                        .accessibilityIdentifier("devices-profile")
                }
                devices(state.pairedDevices)
            }
            .padding(Spacing.page)
        }
        .background(Palette.background.color)
        .navigationTitle(ShellLabel.devicesTitle.text)
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: state.factories.first?.id, initial: true) { _, first in
            if selectedFactory.isEmpty, let first { selectedFactory = first }
        }
        .onChange(of: selectedFactory) { _, id in if !id.isEmpty { model.loadDevices(id) } }
        .sheet(isPresented: confirmationShown) {
            if let confirmation = model.state.confirmation {
                SettingsConfirmationSheet(confirmation: confirmation, model: model)
            }
        }
        .task { await model.observe() }
        .accessibilityIdentifier("paired-devices")
    }

    @ViewBuilder private func devices(_ devices: PairedDevicesUi?) -> some View {
        switch devices?.status {
        case .loading:
            LoadingState(label: ShellLabel.devicesLoading.text).frame(minHeight: 120)
        case .failed:
            Text(L10n.text(PairedDevicesStatus.failed)).font(Typography.body)
                .foregroundStyle(Palette.destructive.color)
                .accessibilityIdentifier("devices-failed")
        case .loaded:
            if let devices {
                if devices.keys.isEmpty {
                    Text(ShellLabel.devicesEmpty.text).font(Typography.body)
                } else {
                    VStack(spacing: Spacing.large) {
                        ForEach(devices.keys, id: \.id) { key in
                            DeviceKeyCard(key: key) {
                                model.requestRevoke(instanceId: devices.instanceId, keyId: key.id)
                            }
                        }
                    }
                }
            }
        case .idle, nil:
            EmptyView()
        }
    }

    private var confirmationShown: Binding<Bool> {
        Binding(
            get: { model.state.confirmation != nil },
            set: { if !$0, model.state.confirmation != nil { model.dismissConfirmation() } })
    }
}

private struct DeviceKeyCard: View {
    let key: DeviceKeyUi
    let onRevoke: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            Text(key.name).font(Typography.cardTitle)
            if key.isCurrentDevice {
                StatusPill(text: ShellLabel.devicesThisPhone.text, tone: .success)
            }
            TickingReference(every: LiveClock.ageTick) { now in
                Text(lastUsed(now: now ?? Date())).font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
            }
            Button(ShellLabel.devicesRevoke.text, action: onRevoke)
                .buttonStyle(.app(.destructive))
                .accessibilityLabel("\(ShellLabel.devicesRevoke.text) \(key.name)")
                .accessibilityIdentifier("revoke-\(key.id)")
        }
        .cardSurface()
        .accessibilityElement(children: .contain)
        .accessibilityLabel(
            key.isCurrentDevice ? "\(key.name), \(ShellLabel.devicesThisPhone.text)" : key.name
        )
        .accessibilityIdentifier("device-key-\(key.id)")
    }

    private func lastUsed(now: Date) -> String {
        guard let millis = key.lastUsedAtEpochMillis?.int64Value else { return ShellLabel.devicesNeverUsed.text }
        let seconds = now.timeIntervalSince(Date(timeIntervalSince1970: Double(millis) / 1000))
        return ShellLabel.devicesLastUsed.text(RelativeAge.text(seconds: seconds))
    }
}
