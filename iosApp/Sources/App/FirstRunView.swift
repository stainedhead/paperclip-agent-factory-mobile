import PaperclipKit
import SwiftUI

/// Screen 15: add the first factory in three steps (Address, Pair, Secure). It renders `FirstRunState` and sends
/// intents; every rule (what is valid, what is enabled, what happened) is shared Kotlin.
struct FirstRunView: View {
    let model: FirstRunModel
    var onTryDemo: (() -> Void)?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.roomy) {
                    header
                    StepIndicator(steps: steps, current: currentStep)
                    if let error = model.state.error {
                        ErrorBanner(error: error, onRetry: model.dismissError)
                    }
                    switch model.state.step {
                    case .address: FirstRunAddressStep(model: model, onTryDemo: onTryDemo)
                    case .pair: FirstRunPairStep(model: model)
                    case .secure, .done: FirstRunSecureStep(model: model)
                    }
                }
                .padding(Spacing.page)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Palette.background.color.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
        .accessibilityIdentifier("first-run")
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            Image(systemName: "server.rack")
                .font(.title2)
                .foregroundStyle(Palette.onAccent.color)
                .frame(width: 48, height: 48)
                .background(Palette.accent.color, in: RoundedRectangle(cornerRadius: Radius.card))
                .accessibilityHidden(true)
            Text(ShellLabel.firstRunHeadline.text)
                .font(Typography.largeTitle)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("first-run-title")
            Text(ShellLabel.firstRunIntro.text)
                .font(Typography.body)
                .foregroundStyle(Palette.textSecondary.color)
        }
    }

    private var steps: [StepIndicator.Step] {
        [
            .init(number: 1, title: ShellLabel.stepAddress.text),
            .init(number: 2, title: ShellLabel.stepPair.text),
            .init(number: 3, title: ShellLabel.stepSecure.text)
        ]
    }

    private var currentStep: Int {
        switch model.state.step {
        case .address: 1
        case .pair: 2
        case .secure, .done: 3
        }
    }
}

/// Step 1: the address and name, the check, and the way on.
private struct FirstRunAddressStep: View {
    let model: FirstRunModel
    var onTryDemo: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            VStack(alignment: .leading, spacing: Spacing.roomy) {
                FormField(
                    title: ShellLabel.fieldAddressLabel.text, placeholder: "https://paperclip.example.com",
                    text: addressBinding, help: addressHelp, issue: state.issue.map { L10n.text($0) },
                    keyboard: .URL, capitalization: .never, identifier: "address-field")
                FormField(
                    title: ShellLabel.fieldNameLabel.text, placeholder: ShellLabel.fieldNamePlaceholder.text,
                    text: nameBinding, help: ShellLabel.fieldNameHelp.text,
                    issue: state.nameIssue.map { L10n.text($0) }, identifier: "name-field")
                Button(action: model.checkAddress) {
                    Label(checkTitle, systemImage: "checkmark.shield")
                }
                .buttonStyle(.app(.secondary))
                .disabled(!state.canContinue || state.probe == .probing)
                .accessibilityIdentifier("check-address-button")
            }
            .cardSurface()
            probeResult
            Text(ShellLabel.firstRunKeyNote.text)
                .font(Typography.secondary)
                .foregroundStyle(Palette.textSecondary.color)
            Button(ShellLabel.firstRunContinueToPairing.text, action: model.continueToPairing)
                .buttonStyle(.app(.primary))
                .disabled(state.probe != .accepted || state.isBusy)
                .accessibilityIdentifier("continue-button")
            if let onTryDemo {
                Button(ShellLabel.firstRunTryDemo.text, action: onTryDemo)
                    .buttonStyle(.app(.muted))
                    .accessibilityIdentifier("try-demo-button")
            }
        }
    }

    private var state: FirstRunState { model.state }

    private var checkTitle: String {
        state.probe == .probing ? ShellLabel.checking.text : ShellLabel.checkAddress.text
    }

    private var addressHelp: String? {
        if let normalized = state.normalizedAddress { return ShellLabel.firstRunWillConnect.text(normalized) }
        return ShellLabel.fieldAddressHelp.text
    }

    private var addressBinding: Binding<String> {
        Binding(get: { state.address }, set: { model.addressChanged($0) })
    }

    private var nameBinding: Binding<String> {
        Binding(get: { state.name }, set: { model.nameChanged($0) })
    }

    @ViewBuilder private var probeResult: some View {
        switch state.probe {
        case .accepted:
            ResultCard(title: L10n.text(ProbeStatus.accepted), tone: .success, identifier: "probe-result") {
                Text(probeDetail)
            }
        case .refused:
            ResultCard(title: L10n.text(ProbeStatus.refused), tone: .critical, identifier: "probe-result") {
                if let refusal = state.probeRefusal { Text(L10n.text(refusal)) }
            }
        case .idle, .probing, .failed:
            EmptyView()
        }
    }

    private var probeDetail: String {
        var parts: [String] = []
        if let exposure = state.exposure { parts.append(L10n.text(exposure)) }
        if let version = state.serverVersion { parts.append(ShellLabel.probeVersion.text(version)) }
        return parts.joined(separator: " \u{00B7} ")
    }
}

/// Step 2: approve this phone in the browser sheet; the shared use case polls until the person decides.
private struct FirstRunPairStep: View {
    let model: FirstRunModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            VStack(alignment: .leading, spacing: Spacing.large) {
                Text(ShellLabel.pairTitle.text).font(Typography.title).accessibilityAddTraits(.isHeader)
                Text(ShellLabel.pairIntro.text).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
                status
            }
            .cardSurface()
            if model.state.pairing == .waiting {
                Button(ShellLabel.pairCancel.text, action: model.cancelPairing)
                    .buttonStyle(.app(.secondary))
                    .accessibilityIdentifier("pair-cancel-button")
            } else {
                Button(openTitle, action: model.pair)
                    .buttonStyle(.app(.primary))
                    .disabled(model.state.isBusy)
                    .accessibilityIdentifier("pair-button")
            }
            Button(ShellLabel.commonBack.text, action: model.back)
                .buttonStyle(.app(.muted))
                .accessibilityIdentifier("back-button")
        }
    }

    private var openTitle: String {
        model.state.pairing == .idle ? ShellLabel.pairOpen.text : ShellLabel.pairTryAgain.text
    }

    @ViewBuilder private var status: some View {
        switch model.state.pairing {
        case .waiting:
            HStack(spacing: Spacing.large) {
                ProgressView()
                Text(L10n.text(PairingStatus.waiting)).font(Typography.body)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("pairing-status")
        case .cancelled, .expired, .failed:
            Text(L10n.text(model.state.pairing))
                .font(Typography.body)
                .foregroundStyle(Palette.warn.color)
                .accessibilityIdentifier("pairing-status")
        case .idle:
            EmptyView()
        }
    }
}

/// Step 3: the key is saved; choose the company that opens first and whether the app locks.
private struct FirstRunSecureStep: View {
    let model: FirstRunModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            Text(ShellLabel.secureTitle.text).font(Typography.title).accessibilityAddTraits(.isHeader)
            Text(ShellLabel.secureIntro.text).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
            if !model.state.companies.isEmpty {
                SectionHeader(title: ShellLabel.secureCompanies.text)
                CardGroup {
                    ForEach(Array(model.state.companies.enumerated()), id: \.element.id) { index, company in
                        if index > 0 { HairlineDivider() }
                        CompanyChoiceRow(
                            company: company, isChosen: model.state.defaultCompanyId == company.id,
                            onChoose: { model.chooseCompany(company.id) })
                    }
                }
            }
            CardGroup {
                Toggle(isOn: appLockBinding) {
                    VStack(alignment: .leading, spacing: Spacing.hairline) {
                        Text(ShellLabel.secureAppLock.text).font(Typography.cardTitle)
                        Text(ShellLabel.secureAppLockHelp.text)
                            .font(Typography.secondary)
                            .foregroundStyle(Palette.textSecondary.color)
                    }
                }
                .frame(minHeight: Spacing.minimumTarget)
                .padding(.vertical, Spacing.small)
                .accessibilityIdentifier("app-lock-toggle")
            }
            Button(action: model.finish) {
                Label(ShellLabel.secureFinish.text, systemImage: "faceid")
            }
            .buttonStyle(.app(.primary))
            .disabled(model.state.isBusy)
            .accessibilityIdentifier("finish-button")
        }
    }

    private var appLockBinding: Binding<Bool> {
        Binding(get: { model.state.appLockEnabled }, set: { model.setAppLock($0) })
    }
}

/// One company in a single-choice list; the role is shown in words.
struct CompanyChoiceRow: View {
    let company: CompanyUi
    let isChosen: Bool
    let onChoose: () -> Void

    var body: some View {
        Button(action: onChoose) {
            CardRow(title: company.name, help: L10n.text(company.role)) {
                Image(systemName: isChosen ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle((isChosen ? Palette.accent : Palette.textSecondary).color)
                    .accessibilityHidden(true)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isChosen ? .isSelected : [])
        .accessibilityIdentifier("company-choice-\(company.id)")
    }
}
