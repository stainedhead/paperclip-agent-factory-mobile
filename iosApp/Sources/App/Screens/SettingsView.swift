import PaperclipKit
import SwiftUI

/// Screen 13: this phone (device name, app lock, lock after, destructive actions), each factory, paired devices and
/// keys, and signing out everywhere. It renders `SettingsState`; every change is an intent.
struct SettingsView: View {
    let environment: AppEnvironment
    private var model: SettingsModel { environment.settingsModel() }
    @State private var nameDraft = ""
    @FocusState private var nameFocused: Bool

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                if let error = state.error { ErrorBanner(error: error, onDismiss: model.dismissMessage) }
                notices(state)
                SectionHeader(title: ShellLabel.settingsThisPhone.text)
                CardGroup {
                    CardRow(title: ShellLabel.settingsDeviceName.text, help: ShellLabel.settingsDeviceNameHelp.text) {
                        TextField(ShellLabel.settingsDeviceName.text, text: $nameDraft)
                            .multilineTextAlignment(.trailing)
                            .font(Typography.body)
                            .submitLabel(.done)
                            .focused($nameFocused)
                            .onSubmit { model.commitDeviceName(nameDraft) }
                            // Leaving the field or the screen saves the name too, not only the keyboard's Done.
                            .onChange(of: nameFocused) { _, focused in if !focused { commitNameIfChanged() } }
                            .onDisappear { commitNameIfChanged() }
                            .frame(
                                minWidth: 120, maxWidth: .infinity, minHeight: Spacing.minimumTarget,
                                alignment: .trailing
                            )
                            .contentShape(Rectangle())
                            .accessibilityLabel(ShellLabel.settingsDeviceName.text)
                            .accessibilityIdentifier("settings-device-name")
                    }
                    if state.deviceNameInvalid {
                        Text(ShellLabel.settingsDeviceNameInvalid.text)
                            .font(Typography.secondary).foregroundStyle(Palette.destructive.color)
                            .accessibilityIdentifier("settings-device-name-issue")
                    }
                    HairlineDivider()
                    Toggle(ShellLabel.settingsAppLock.text, isOn: lockBinding)
                        .font(Typography.cardTitle)
                        .frame(minHeight: Spacing.minimumTarget)
                        .padding(.vertical, Spacing.small)
                        .accessibilityIdentifier("settings-app-lock-toggle")
                }
                CardGroup {
                    CardRow(title: ShellLabel.settingsIdlePeriod.text) {
                        Picker(ShellLabel.settingsIdlePeriod.text, selection: idleBinding) {
                            ForEach(state.idleOptions, id: \.self) { Text(L10n.text($0)).tag($0) }
                        }
                        .pickerStyle(.menu)
                        .frame(minHeight: Spacing.minimumTarget)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("settings-idle-period")
                }
                CardGroup {
                    Toggle(isOn: .constant(state.destructiveActionAuthAlwaysOn)) {
                        VStack(alignment: .leading, spacing: Spacing.hairline) {
                            Text(ShellLabel.settingsDestructive.text).font(Typography.cardTitle)
                            Text(ShellLabel.settingsDestructiveHelp.text).font(Typography.secondary)
                                .foregroundStyle(Palette.textSecondary.color)
                        }
                    }
                    .disabled(true)
                    .frame(minHeight: Spacing.minimumTarget)
                    .padding(.vertical, Spacing.small)
                    .accessibilityIdentifier("settings-destructive-toggle")
                }
                SectionHeader(title: ShellLabel.settingsEachFactory.text)
                factories(state)
                SectionHeader(title: ShellLabel.settingsKeysAndDevices.text)
                CardGroup {
                    Button {
                        environment.router.push(.pairedDevices, in: .factories)
                    } label: {
                        CardRow(title: ShellLabel.settingsPairedDevices.text) {
                            Image(systemName: "chevron.right").foregroundStyle(Palette.textSecondary.color)
                                .accessibilityHidden(true)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("settings-paired-devices")
                }
                Text(ShellLabel.settingsAccountHelp.text).font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
                Button(action: model.signOutEverywhere) {
                    Label(ShellLabel.settingsSignOutEverywhere.text, systemImage: "rectangle.portrait.and.arrow.right")
                }
                .buttonStyle(.app(.destructive))
                .disabled(state.isBusy)
                .accessibilityIdentifier("settings-sign-out-everywhere")
            }
            .padding(Spacing.page)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Palette.background.color)
        .navigationTitle(ShellLabel.settingsTitle.text)
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: state.deviceName, initial: true) { _, name in nameDraft = name }
        .sheet(isPresented: confirmationShown) {
            if let confirmation = model.state.confirmation {
                SettingsConfirmationSheet(confirmation: confirmation, model: model)
            }
        }
        .task { await model.observe() }
        .accessibilityIdentifier("settings")
    }

    private func commitNameIfChanged() {
        if nameDraft != model.state.deviceName { model.commitDeviceName(nameDraft) }
    }

    @ViewBuilder private func notices(_ state: SettingsState) -> some View {
        if let outcome = state.lastSignOutKey {
            Text(L10n.text(outcome)).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                .accessibilityIdentifier("settings-key-outcome")
        }
        if state.unconfirmedRevokes > 0 {
            Text(ShellLabel.settingsUnconfirmedRevokes.plural(Int(state.unconfirmedRevokes)))
                .font(Typography.secondary).foregroundStyle(Palette.warn.color)
                .accessibilityIdentifier("settings-unconfirmed")
        }
        if state.signOutFailures > 0 {
            Text(ShellLabel.settingsSignOutFailures.plural(Int(state.signOutFailures)))
                .font(Typography.secondary).foregroundStyle(Palette.destructive.color)
                .accessibilityIdentifier("settings-signout-failures")
        }
    }

    private func factories(_ state: SettingsState) -> some View {
        CardGroup {
            ForEach(Array(state.factories.enumerated()), id: \.element.id) { index, factory in
                if index > 0 { HairlineDivider() }
                let status = FactoryStatusPresentation(status: factory.status, expiry: factory.expiry)
                Button {
                    environment.router.push(.editFactory(id: factory.id), in: .factories)
                } label: {
                    CardRow(title: factory.name) {
                        Text(status.text).font(Typography.body).foregroundStyle(status.tone.foreground.color)
                        Image(systemName: "chevron.right").foregroundStyle(Palette.textSecondary.color)
                            .accessibilityHidden(true)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("settings-factory-\(factory.id)")
            }
        }
    }

    private var lockBinding: Binding<Bool> {
        Binding(get: { model.state.appLockEnabled }, set: { model.toggleAppLock($0) })
    }

    private var idleBinding: Binding<IdlePeriod> {
        Binding(get: { model.state.idlePeriod }, set: { model.chooseIdle($0) })
    }

    private var confirmationShown: Binding<Bool> {
        Binding(
            get: { model.state.confirmation != nil },
            set: { if !$0, model.state.confirmation != nil { model.dismissConfirmation() } })
    }
}

/// The question before signing out or revoking, worded from the confirmation the shared state holds.
struct SettingsConfirmationSheet: View {
    let confirmation: SettingsConfirmation
    let model: SettingsModel

    var body: some View {
        switch confirmation {
        case let signOut as SettingsConfirmationSignOut:
            ConfirmSheet(
                title: ShellLabel.editSignOutTitle.text(signOut.name), message: ShellLabel.editSignOutMessage.text,
                confirmTitle: ShellLabel.editSignOutConfirm.text, onConfirm: model.confirm,
                onCancel: model.dismissConfirmation)
        case let revoke as SettingsConfirmationRevokeKey:
            ConfirmSheet(
                title: ShellLabel.revokeTitle.text(revoke.name),
                message: revoke.isCurrentDevice
                    ? ShellLabel.revokeThisPhoneMessage.text : ShellLabel.revokeMessage.text,
                confirmTitle: ShellLabel.revokeConfirm.text, onConfirm: model.confirm,
                onCancel: model.dismissConfirmation)
        default:
            ConfirmSheet(
                title: ShellLabel.settingsSignOutEverywhereTitle.text,
                message: ShellLabel.settingsSignOutEverywhereMessage.text,
                confirmTitle: ShellLabel.settingsSignOutEverywhereConfirm.text, onConfirm: model.confirm,
                onCancel: model.dismissConfirmation)
        }
    }
}
