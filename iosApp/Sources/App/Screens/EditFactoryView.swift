import PaperclipKit
import SwiftUI

/// Screen 9: add a factory, or edit one. Name, address with its check, default company, the person's read-only access
/// and roles, the certificate pin, and pair again, sign out and remove. It renders `EditInstanceState`: Save is
/// enabled when the state says so, and a changed address cannot be saved before it is checked.
struct EditFactoryView: View {
    let environment: AppEnvironment
    let instanceId: String?
    private var model: EditModel { environment.editModel(id: instanceId) }

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                if let error = state.error { ErrorBanner(error: error, onDismiss: model.dismissMessage) }
                if state.isOffline {
                    Label(ShellLabel.editOfflineNote.text, systemImage: "wifi.slash")
                        .font(Typography.secondary).foregroundStyle(Palette.warn.color)
                        .accessibilityIdentifier("edit-offline")
                }
                if let status = state.status { statusLine(status: status, expiry: state.expiry) }
                SectionHeader(title: ShellLabel.editSectionFactory.text)
                EditFactoryFields(model: model)
                if !state.companies.isEmpty { EditCompaniesSection(model: model) }
                SectionHeader(title: ShellLabel.editSectionSecurity.text)
                EditPinSection(model: model)
                if state.instanceId != nil { EditActions(model: model) }
                if let outcome = state.serverKeyOutcome {
                    Text(L10n.text(outcome)).font(Typography.secondary)
                        .foregroundStyle(Palette.textSecondary.color)
                        .accessibilityIdentifier("edit-key-outcome")
                }
            }
            .padding(Spacing.page)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Palette.background.color)
        .navigationTitle(state.mode == .add ? ShellLabel.editTitleAdd.text : ShellLabel.editTitleEdit.text)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(ShellLabel.commonCancel.text) { environment.router.pop(.factories) }
                    .accessibilityIdentifier("edit-cancel-button")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(ShellLabel.commonSave.text, action: model.save)
                    .disabled(!state.canSave || state.isBusy)
                    .accessibilityIdentifier("edit-save-button")
            }
        }
        .onChange(of: state.saved) { _, saved in if saved { environment.router.pop(.factories) } }
        .onChange(of: state.removed) { _, removed in if removed { environment.router.popToRoot(.factories) } }
        .sheet(isPresented: confirmationShown) {
            if let which = model.state.confirmation { sheet(for: which, state: model.state) }
        }
        .task { await model.observe() }
        .accessibilityIdentifier("edit-factory")
    }

    private func statusLine(status: FactoryStatusUi, expiry: ExpiryUi?) -> some View {
        let presentation = FactoryStatusPresentation(
            status: status, expiry: expiry ?? ExpiryUi(state: .noExpiry, daysLeft: nil))
        return StatusPill(
            text: presentation.text, tone: presentation.tone.pillTone
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(presentation.text)
        .accessibilityIdentifier("edit-status")
    }

    private var confirmationShown: Binding<Bool> {
        Binding(
            get: { model.state.confirmation != nil },
            set: { if !$0, model.state.confirmation != nil { model.dismissConfirmation() } })
    }

    @ViewBuilder private func sheet(for which: EditConfirmation, state: EditInstanceState) -> some View {
        switch which {
        case .signOut:
            ConfirmSheet(
                title: ShellLabel.editSignOutTitle.text(state.name),
                message: ShellLabel.editSignOutMessage.text,
                confirmTitle: ShellLabel.editSignOutConfirm.text,
                isWorking: state.isBusy, onConfirm: model.confirm, onCancel: model.dismissConfirmation)
        case .remove:
            ConfirmDestructiveSheet(
                title: ShellLabel.removeTitle.text(state.name),
                message: ShellLabel.editRemoveMessage.text,
                confirmTitle: ShellLabel.removeConfirm.text, issue: state.deviceAuthIssue,
                isWorking: state.isBusy, cancelTitle: ShellLabel.commonKeep.text,
                onConfirm: model.confirm, onCancel: model.dismissConfirmation)
        }
    }
}

extension StatusTone {
    var pillTone: StatusPill.Tone {
        switch self {
        case .success: .success
        case .warn: .warn
        case .critical: .critical
        case .neutral: .neutral
        }
    }
}

private struct EditFactoryFields: View {
    let model: EditModel

    var body: some View {
        let state = model.state
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            FormField(
                title: ShellLabel.fieldNameLabel.text, placeholder: ShellLabel.fieldNamePlaceholder.text,
                text: Binding(get: { model.state.name }, set: { model.nameChanged($0) }),
                help: ShellLabel.fieldNameHelp.text, issue: state.nameIssue.map { L10n.text($0) },
                identifier: "edit-name")
            FormField(
                title: ShellLabel.fieldAddressLabel.text, placeholder: "https://paperclip.example.com",
                text: Binding(get: { model.state.address }, set: { model.addressChanged($0) }),
                help: ShellLabel.fieldAddressHelp.text, issue: state.addressIssue.map { L10n.text($0) },
                keyboard: .URL, capitalization: .never, identifier: "edit-address")
            if state.addressChangeClearsKey {
                Text(ShellLabel.editAddressChangeWarning.text)
                    .font(Typography.secondary).foregroundStyle(Palette.warn.color)
                    .accessibilityIdentifier("edit-address-warning")
            }
            Button(action: model.checkAddress) {
                Label(
                    state.probe == .probing ? ShellLabel.checking.text : ShellLabel.checkAddress.text,
                    systemImage: "checkmark.shield")
            }
            .buttonStyle(.app(.secondary))
            .disabled(!state.canCheckAddress)
            .accessibilityIdentifier("edit-check-address")
            probe(state)
        }
        .cardSurface()
    }

    @ViewBuilder private func probe(_ state: EditInstanceState) -> some View {
        switch state.probe {
        case .accepted:
            ResultCard(title: L10n.text(ProbeStatus.accepted), tone: .success, identifier: "edit-probe-result") {
                Text(detail(state))
            }
        case .refused:
            ResultCard(title: L10n.text(ProbeStatus.refused), tone: .critical, identifier: "edit-probe-result") {
                if let refusal = state.probeRefusal { Text(L10n.text(refusal)) }
            }
        case .idle, .probing, .failed:
            EmptyView()
        }
    }

    private func detail(_ state: EditInstanceState) -> String {
        var parts: [String] = []
        if let exposure = state.exposure { parts.append(L10n.text(exposure)) }
        if let version = state.serverVersion { parts.append(ShellLabel.probeVersion.text(version)) }
        return parts.joined(separator: " \u{00B7} ")
    }
}

private struct EditCompaniesSection: View {
    let model: EditModel

    var body: some View {
        let state = model.state
        SectionHeader(title: ShellLabel.editSectionCompanies.text)
        CardGroup {
            CardRow(title: ShellLabel.editOpenOnLaunch.text) {
                Picker(ShellLabel.editOpenOnLaunch.text, selection: chosen) {
                    ForEach(state.companies, id: \.id) { Text($0.name).tag($0.id) }
                }
                .pickerStyle(.menu)
                .frame(minHeight: Spacing.minimumTarget)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("edit-default-company")
        }
        SectionHeader(
            title: state.profileName.map { ShellLabel.editSectionAccess.text($0) }
                ?? ShellLabel.editSectionAccessAnonymous.text)
        CardGroup {
            ForEach(state.companies, id: \.id) { company in
                CardRow(title: company.name) {
                    Text(L10n.text(company.role)).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("edit-access-company-\(company.id)")
                HairlineDivider()
            }
            CardRow(title: ShellLabel.editBoardKey.text) {
                Text(ExpiryPresentation(state.expiry ?? ExpiryUi(state: .noExpiry, daysLeft: nil)).text ?? "")
                    .font(Typography.body).foregroundStyle(Palette.textSecondary.color)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("edit-board-key")
        }
    }

    private var chosen: Binding<String> {
        Binding(
            get: { model.state.defaultCompanyId ?? model.state.companies.first?.id ?? "" },
            set: { model.chooseCompany($0) })
    }
}

private struct EditPinSection: View {
    let model: EditModel
    @State private var pinText = ""

    var body: some View {
        let state = model.state
        CardGroup {
            Toggle(
                isOn: Binding(get: { model.state.pinningEnabled }, set: { model.togglePinning($0) })
            ) {
                VStack(alignment: .leading, spacing: Spacing.hairline) {
                    Text(ShellLabel.editPinTitle.text).font(Typography.cardTitle)
                    Text(ShellLabel.editPinHelp.text).font(Typography.secondary)
                        .foregroundStyle(Palette.textSecondary.color)
                }
            }
            .frame(minHeight: Spacing.minimumTarget)
            .padding(.vertical, Spacing.small)
            .accessibilityIdentifier("edit-pin-toggle")
            if state.pinningEnabled {
                ForEach(state.pins, id: \.self) { pin in
                    HStack {
                        Text(pin).font(Typography.secondary).lineLimit(1).truncationMode(.middle)
                        Spacer()
                        Button(role: .destructive) {
                            model.removePin(pin)
                        } label: {
                            Image(systemName: "minus.circle")
                                .frame(minWidth: Spacing.minimumTarget, minHeight: Spacing.minimumTarget)
                        }
                        .accessibilityLabel(ShellLabel.commonRemove.text)
                    }
                }
            }
            if state.pinEntryOpen {
                VStack(alignment: .leading, spacing: Spacing.medium) {
                    FormField(
                        title: ShellLabel.editPinTitle.text, placeholder: ShellLabel.editPinPlaceholder.text,
                        text: $pinText, issue: state.pinInvalid ? ShellLabel.editPinInvalid.text : nil,
                        capitalization: .never, identifier: "edit-pin-field")
                    Button(ShellLabel.editPinAdd.text) {
                        model.addPin(pinText)
                        pinText = ""
                    }
                    .buttonStyle(.app(.secondary))
                    .accessibilityIdentifier("edit-pin-add")
                }
                .padding(.vertical, Spacing.medium)
            }
        }
    }
}

private struct EditActions: View {
    let model: EditModel

    var body: some View {
        let state = model.state
        VStack(alignment: .leading, spacing: Spacing.large) {
            if state.pairing == .waiting {
                HStack(spacing: Spacing.large) {
                    ProgressView()
                    Text(L10n.text(PairingStatus.waiting)).font(Typography.body)
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("edit-pairing-status")
                Button(ShellLabel.pairCancel.text, action: model.cancelPairing)
                    .buttonStyle(.app(.secondary))
                    .accessibilityIdentifier("edit-pair-cancel")
            } else {
                if state.pairing != .idle {
                    Text(L10n.text(state.pairing)).font(Typography.body).foregroundStyle(Palette.warn.color)
                        .accessibilityIdentifier("edit-pairing-status")
                }
                HStack(spacing: Spacing.large) {
                    Button(ShellLabel.editPairAgain.text, action: model.pairAgain)
                        .buttonStyle(.app(.secondary))
                        .disabled(state.isOffline || state.isBusy)
                        .accessibilityIdentifier("edit-pair-again")
                    Button(ShellLabel.editSignOut.text, action: model.signOut)
                        .buttonStyle(.app(.secondary))
                        .disabled(state.isBusy)
                        .accessibilityIdentifier("edit-sign-out")
                }
            }
            Button(action: model.remove) {
                Label(ShellLabel.editRemove.text, systemImage: "lock.fill")
            }
            .buttonStyle(.app(.destructive))
            .disabled(state.isBusy)
            .accessibilityIdentifier("edit-remove")
        }
    }
}
